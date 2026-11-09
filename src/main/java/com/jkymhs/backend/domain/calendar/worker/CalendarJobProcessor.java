package com.jkymhs.backend.domain.calendar.worker;

import com.jkymhs.backend.domain.calendar.client.google.GoogleApiException;
import com.jkymhs.backend.domain.calendar.client.google.GoogleAuthExpiredException;
import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarClient;
import com.jkymhs.backend.domain.calendar.client.google.GoogleEvent;
import com.jkymhs.backend.domain.calendar.crypto.CalendarTokenCipher;
import com.jkymhs.backend.domain.calendar.entity.CalendarFailureReason;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobOperation;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJobItem;
import com.jkymhs.backend.domain.calendar.service.CalendarAutoSyncService;
import com.jkymhs.backend.domain.trip.service.CalendarItem;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 작업 하나의 한 차례를 처리한다 (항목 최대 batch-size개, 구글 호출은 하나씩 차례로 — §5 Q7).
 *
 * 1) 액세스 토큰: 리프레시 토큰으로 새로 받는다. invalid_grant → 연동 REVOKED, 남은 항목 AUTH_EXPIRED, 작업 FAILED
 * 2) 항목마다 구글 호출. 결과는 {@link CalendarJobStore}로 바로 기록한다 (중간에 서버가 죽어도 끝난 항목은 다시 하지 않게)
 *    - 429·5xx·응답 없음 → 항목 시도 +1, 작업을 백오프하고 이번 차례를 멈춘다 (최대 3번)
 *    - 403 → NOT_WRITABLE, 그 밖의 4xx → GOOGLE_ERROR로 그 항목만 실패
 * 3) 남은 항목이 있으면 바로 다음 차례로, 없으면 작업을 끝낸다 → 해제·탈퇴 작업이면 토큰 폐기 → 다룬 여행의 남은 DIRTY 항목 다시 맞추기
 *
 * 할 일은 작업 항목의 operation보다 "지금 항목 상태"를 따른다: 일정이 아직 없으면 만들고, 다른 캘린더에 있으면 옮기고, 있으면 고친다.
 * 작업이 기다리는 동안 다른 작업이 먼저 올렸거나 지웠어도 결과가 맞게 하기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarJobProcessor {

    private final CalendarJobStore store;
    private final GoogleCalendarClient googleCalendarClient;
    private final CalendarTokenCipher tokenCipher;
    private final TripCalendarSupport tripCalendarSupport;
    private final CalendarEventFactory eventFactory;
    private final CalendarAutoSyncService autoSyncService;

    private record Applied(String calendarId, String eventId, CalendarItem item, boolean firstCreate) {
    }

    public void process(Long jobId) {
        try {
            processRound(jobId);
        } catch (RuntimeException e) {
            // 예상 못 한 오류(DB 일시 장애·키 문제 등)로 작업이 RUNNING에 멈추지 않게, 작업 단위로 미루거나 끝낸다
            log.error("캘린더 작업 {} 처리 실패", jobId, e);
            if (store.backoffOrGiveUp(jobId, e.getClass().getSimpleName())) {
                complete(jobId);
            }
        }
    }

    private void processRound(Long jobId) {
        Optional<CalendarJobStore.Round> found = store.round(jobId);
        if (found.isEmpty()) {
            return;
        }
        CalendarJobStore.Round round = found.get();
        if (round.items().isEmpty()) {
            complete(jobId);
            return;
        }

        String accessToken;
        try {
            String encrypted = round.encryptedRefreshToken() != null ? round.encryptedRefreshToken()
                    : store.activeLinkToken(round.userId()).orElse(null);
            if (encrypted == null) {
                // 작업을 넣은 뒤 연동을 끊었거나 만료됐다
                store.failPending(jobId, CalendarFailureReason.AUTH_EXPIRED);
                complete(jobId);
                return;
            }
            accessToken = googleCalendarClient.refreshAccessToken(tokenCipher.decrypt(encrypted));
        } catch (GoogleAuthExpiredException e) {
            store.revokeLink(round.userId(), round.encryptedRefreshToken());
            store.failPending(jobId, CalendarFailureReason.AUTH_EXPIRED);
            complete(jobId);
            return;
        } catch (GoogleApiException e) {
            if (!e.retryable()) {
                store.failPending(jobId, CalendarFailureReason.GOOGLE_ERROR);
                complete(jobId);
            } else if (store.backoffOrGiveUp(jobId, e.getMessage())) {
                complete(jobId);
            }
            return;
        }

        for (CalendarSyncJobItem item : round.items()) {
            try {
                Applied applied = apply(accessToken, item);
                store.succeeded(round.userId(), item.getId(), applied.calendarId(), applied.eventId(), applied.item(),
                        applied.firstCreate());
            } catch (GoogleApiException e) {
                if (e.retryable()) {
                    store.retryLater(jobId, item.getId(), e.getMessage());
                    return;
                }
                store.failed(item.getId(), reasonOf(e));
            }
        }
        if (store.hasPending(jobId)) {
            store.continueNow(jobId);
        } else {
            complete(jobId);
        }
    }

    private static CalendarFailureReason reasonOf(GoogleApiException e) {
        if (e.forbidden()) {
            return CalendarFailureReason.NOT_WRITABLE;
        }
        return e.status() == 401 ? CalendarFailureReason.AUTH_EXPIRED : CalendarFailureReason.GOOGLE_ERROR;
    }

    private Applied apply(String accessToken, CalendarSyncJobItem jobItem) {
        if (jobItem.getOperation() == CalendarJobOperation.DELETE) {
            try {
                googleCalendarClient.deleteEvent(accessToken, jobItem.getCalendarId(), jobItem.getEventId());
            } catch (GoogleApiException e) {
                // 이미 없는 일정(사용자가 구글에서 지움)은 지운 것으로 본다
                if (!e.notFound()) {
                    throw e;
                }
            }
            return new Applied(jobItem.getCalendarId(), jobItem.getEventId(), null, false);
        }

        Optional<CalendarItem> current = Optional.ofNullable(jobItem.getPlanItemId()).flatMap(tripCalendarSupport::item);
        if (current.isEmpty()) {
            // 작업이 기다리는 사이 항목이 지워졌다 (그 일정 삭제는 삭제 작업이 맡는다)
            return new Applied(jobItem.getCalendarId(), jobItem.getEventId(), null, false);
        }
        CalendarItem item = current.get();
        String target = jobItem.getCalendarId();
        String eventId = item.hasEvent() ? item.eventId() : jobItem.getEventId();
        GoogleEvent event = eventFactory.build(item, eventId);

        if (!item.hasEvent()) {
            insertOrRevive(accessToken, target, event);
            return new Applied(target, eventId, item, true);
        }
        if (item.calendarId() != null && !item.calendarId().equals(target)) {
            try {
                googleCalendarClient.moveEvent(accessToken, item.calendarId(), eventId, target);
            } catch (GoogleApiException e) {
                if (!e.notFound()) {
                    throw e;
                }
                // 예전 캘린더에서 사라졌다 → 새 캘린더에 새로 만든다
                insertOrRevive(accessToken, target, event);
                return new Applied(target, eventId, item, false);
            }
        }
        try {
            googleCalendarClient.updateEvent(accessToken, target, event);
        } catch (GoogleApiException e) {
            if (!e.notFound()) {
                throw e;
            }
            insertOrRevive(accessToken, target, event);
        }
        return new Applied(target, eventId, item, false);
    }

    /**
     * 새 일정. 같은 id가 이미 있으면(409) 그 일정을 읽어서, 지워진 일정(cancelled)이면 되살리고 아니면 내용을 고친다.
     * 구글은 지운 일정의 id를 한동안 cancelled로 붙잡고 있어서, 지웠다가 다시 올리면(확정안을 되돌림 등) 409가 난다.
     */
    private void insertOrRevive(String accessToken, String calendarId, GoogleEvent event) {
        try {
            googleCalendarClient.insertEvent(accessToken, calendarId, event);
        } catch (GoogleApiException e) {
            if (!e.conflict()) {
                throw e;
            }
            String status = googleCalendarClient.eventStatus(accessToken, calendarId, event.id());
            if ("cancelled".equals(status)) {
                googleCalendarClient.reviveEvent(accessToken, calendarId, event);
            } else {
                googleCalendarClient.updateEvent(accessToken, calendarId, event);
            }
        }
    }

    private void complete(Long jobId) {
        CalendarJobStore.Finished finished = store.finish(jobId);
        if (finished.revokeAfter() && store.removeLinkIfSameToken(finished.userId(), finished.encryptedRefreshToken())) {
            revokeQuietly(finished.encryptedRefreshToken());
        }
        for (Long tripId : finished.tripIds()) {
            try {
                autoSyncService.syncTrip(finished.userId(), tripId);
            } catch (RuntimeException e) {
                log.error("캘린더 작업 {} 뒤 여행 {} 다시 맞추기 실패", jobId, tripId, e);
            }
        }
    }

    private void revokeQuietly(String encrypted) {
        if (encrypted == null) {
            return;
        }
        try {
            googleCalendarClient.revoke(tokenCipher.decrypt(encrypted));
        } catch (CustomException e) {
            log.warn("캘린더 토큰을 복호화하지 못해 폐기를 건너뜀");
        }
    }
}
