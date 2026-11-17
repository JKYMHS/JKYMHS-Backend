package com.jkymhs.backend.domain.calendar.worker;

import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarProperties;
import com.jkymhs.backend.domain.calendar.entity.CalendarFailureReason;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobItemStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobOperation;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJob;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJobItem;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobItemRepository;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobRepository;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.service.CalendarItem;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 작업자가 쓰는 DB 읽기·쓰기. 메서드 하나가 트랜잭션 하나다.
 *
 * 작업자는 구글 호출(수십 초 걸릴 수 있음)을 트랜잭션 밖에서 하고, 호출 결과 하나마다 여기 메서드로 짧게 기록한다.
 * 작업 항목 상태와 일정 항목의 캘린더 상태(SYNCED·FAILED)를 같은 트랜잭션에서 바꿔서 둘이 어긋나지 않게 한다.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CalendarJobStore {

    // 백오프 간격이 끝없이 길어지지 않게 (5초 × 2^6 ≈ 5분)
    private static final int MAX_BACKOFF_EXPONENT = 6;

    private final CalendarSyncJobRepository jobRepository;
    private final CalendarSyncJobItemRepository jobItemRepository;
    private final GoogleCalendarLinkRepository linkRepository;
    private final TripCalendarSupport tripCalendarSupport;
    private final GoogleCalendarProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 작업 한 차례 분량.
     *
     * @param encryptedRefreshToken DELETE_ALL의 토큰 사본 (없으면 연동 행의 토큰을 쓴다)
     */
    public record Round(Long jobId, Long userId, String encryptedRefreshToken, List<CalendarSyncJobItem> items) {
    }

    /**
     * @param tripIds 작업이 다룬 여행들 (끝난 뒤 남은 DIRTY 항목을 다시 맞출 때 쓴다)
     */
    public record Finished(Long userId, CalendarJobStatus status, boolean revokeAfter, String encryptedRefreshToken,
                           List<Long> tripIds) {
    }

    // ---------- 집기 ----------

    /** 차례가 된 작업을 잠가서 RUNNING으로 바꾸고 id를 돌려준다 (커밋되면 잠금이 풀리고, 다른 서버는 RUNNING이라 집지 않는다) */
    public List<Long> claimDue(int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CalendarSyncJob> jobs = jobRepository.findDueForUpdate(now, limit);
        jobs.forEach(job -> job.claim(now.plus(properties.lease())));
        return jobs.stream().map(CalendarSyncJob::getId).toList();
    }

    /** 실행기가 꽉 차서 맡기지 못했다. 바로 다시 집을 수 있게 되돌린다 */
    public void release(Long jobId) {
        jobRepository.findById(jobId).ifPresent(job -> job.release(LocalDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public Optional<Round> round(Long jobId) {
        return jobRepository.findById(jobId).filter(job -> job.getStatus() == CalendarJobStatus.RUNNING)
                .map(job -> new Round(job.getId(), job.getUserId(), job.getEncryptedRefreshToken(),
                        jobItemRepository.findAllByJobIdAndStatusOrderByIdAsc(job.getId(), CalendarJobItemStatus.PENDING,
                                PageRequest.of(0, properties.batchSize()))));
    }

    @Transactional(readOnly = true)
    public Optional<String> activeLinkToken(Long userId) {
        return linkRepository.findByUserId(userId).filter(GoogleCalendarLink::isActive)
                .map(GoogleCalendarLink::getEncryptedRefreshToken);
    }

    /** 구글이 토큰을 거부했다. 토큰 사본으로 돌던 작업이면, 연동 행이 같은 토큰일 때만 만료로 바꾼다 (그사이 다시 연동했을 수 있다) */
    public void revokeLink(Long userId, String snapshotToken) {
        linkRepository.findByUserIdForUpdate(userId)
                .filter(link -> snapshotToken == null || snapshotToken.equals(link.getEncryptedRefreshToken()))
                .ifPresent(GoogleCalendarLink::revoke);
    }

    // ---------- 항목 결과 ----------

    /**
     * 구글에 반영했다. 일정 항목을 SYNCED로(삭제면 캘린더 정보를 비우게) 바꾸고,
     * 처음 만든 일정이면 행동 데이터 신호(CALENDAR, 가중치 5)를 낸다. 신호는 받는 쪽이 트랜잭션 이벤트로 받아도 되게 트랜잭션 안에서 낸다.
     *
     * @param planItem 지금 항목 값 (삭제거나 항목이 지워졌으면 null)
     */
    public void succeeded(Long userId, Long jobItemId, String calendarId, String eventId, CalendarItem planItem,
                          boolean firstCreate) {
        CalendarSyncJobItem item = jobItemRepository.findById(jobItemId).orElseThrow();
        item.done();
        if (item.getPlanItemId() == null) {
            return;
        }
        if (item.getOperation() == CalendarJobOperation.DELETE) {
            tripCalendarSupport.clearEvent(item.getPlanItemId(), calendarId, eventId);
            return;
        }
        if (planItem != null) {
            tripCalendarSupport.markSynced(item.getPlanItemId(), calendarId, eventId);
            if (firstCreate && planItem.contentId() != null) {
                eventPublisher.publishEvent(new BehaviorSignalEvent(userId, planItem.contentId(), BehaviorSignalType.CALENDAR, planItem.companion(), planItem.tripId(),
                        LocalDateTime.now(clock)));
            }
        }
    }

    public void failed(Long jobItemId, CalendarFailureReason reason) {
        CalendarSyncJobItem item = jobItemRepository.findById(jobItemId).orElseThrow();
        item.fail(reason);
        markPlanItemFailed(item);
    }

    /**
     * 429·5xx: 항목 시도 횟수를 올린다 (최대 횟수면 실패로 끝낸다). 작업은 "5초 × 2^(시도 횟수 - 1)" 뒤로 미룬다
     * — 구글이 사용량 제한을 걸었을 때 바로 다음 항목을 부르면 또 거절당하기 때문이다.
     */
    public void retryLater(Long jobId, Long jobItemId, String error) {
        CalendarSyncJobItem item = jobItemRepository.findById(jobItemId).orElseThrow();
        if (!item.retryLater(properties.maxAttempts())) {
            markPlanItemFailed(item);
        }
        Duration delay = backoffDelay(item.getAttemptCount() - 1);
        jobRepository.findById(jobId).ifPresent(job -> job.delay(LocalDateTime.now(clock).plus(delay), error));
    }

    /** 남은 항목을 모두 실패로 (토큰 만료·연동 없음) */
    public void failPending(Long jobId, CalendarFailureReason reason) {
        jobItemRepository.findAllByJobIdAndStatus(jobId, CalendarJobItemStatus.PENDING).forEach(item -> {
            item.fail(reason);
            markPlanItemFailed(item);
        });
    }

    private void markPlanItemFailed(CalendarSyncJobItem item) {
        if (item.getStatus() == CalendarJobItemStatus.FAILED && item.getPlanItemId() != null
                && item.getOperation() != CalendarJobOperation.DELETE) {
            tripCalendarSupport.markFailed(item.getPlanItemId());
        }
    }

    // ---------- 작업 결과 ----------

    /**
     * 작업 단위 실패(토큰 발급 5xx·예상 못 한 예외). 최대 횟수 전이면 미루고 false, 다 썼으면 남은 항목을 실패로 바꾸고 true.
     */
    public boolean backoffOrGiveUp(Long jobId, String error) {
        CalendarSyncJob job = jobRepository.findById(jobId).orElseThrow();
        if (job.getAttemptCount() + 1 >= properties.maxAttempts()) {
            job.finish(job.getStatus(), job.getSucceeded(), error);
            failPending(jobId, CalendarFailureReason.GOOGLE_ERROR);
            return true;
        }
        job.backoff(LocalDateTime.now(clock).plus(backoffDelay(job.getAttemptCount())), error);
        return false;
    }

    @Transactional(readOnly = true)
    public boolean hasPending(Long jobId) {
        return jobItemRepository.countByJobIdAndStatus(jobId, CalendarJobItemStatus.PENDING) > 0;
    }

    /** 이번 차례는 끝났고 남은 항목이 있다. 바로 다음 차례에 이어서 한다 */
    public void continueNow(Long jobId) {
        jobRepository.findById(jobId).ifPresent(job -> job.release(LocalDateTime.now(clock)));
    }

    /** 모든 항목이 끝났다: 하나도 실패 안 했으면 DONE, 모두 실패면 FAILED, 섞였으면 PARTIAL_FAILED */
    public Finished finish(Long jobId) {
        CalendarSyncJob job = jobRepository.findById(jobId).orElseThrow();
        List<CalendarSyncJobItem> items = jobItemRepository.findAllByJobIdOrderByIdAsc(jobId);
        int done = (int) items.stream().filter(item -> item.getStatus() == CalendarJobItemStatus.DONE).count();
        int failed = (int) items.stream().filter(item -> item.getStatus() == CalendarJobItemStatus.FAILED).count();
        CalendarJobStatus status = failed == 0 ? CalendarJobStatus.DONE
                : done == 0 ? CalendarJobStatus.FAILED : CalendarJobStatus.PARTIAL_FAILED;
        job.finish(status, done, null);
        Set<Long> tripIds = new LinkedHashSet<>();
        if (job.getTripId() != null) {
            tripIds.add(job.getTripId());
        }
        items.stream().map(CalendarSyncJobItem::getTripId).filter(Objects::nonNull).forEach(tripIds::add);
        return new Finished(job.getUserId(), status, job.isRevokeAfter(), job.getEncryptedRefreshToken(),
                job.getJobType() == CalendarJobType.DELETE_ALL ? List.of() : List.copyOf(tripIds));
    }

    /**
     * 해제·탈퇴 작업이 끝났다. 연동 행이 이 작업의 토큰 그대로면(그사이 다시 연동하지 않았으면) 지우고 항목의 캘린더 정보를 비운다.
     *
     * @return 토큰을 폐기해도 되면 true (연동이 이미 없거나 방금 지웠다). 다시 연동했으면 false — 같은 구글 계정이면
     *         예전 토큰을 폐기할 때 새 연동까지 끊길 수 있다
     */
    public boolean removeLinkIfSameToken(Long userId, String snapshotToken) {
        Optional<GoogleCalendarLink> link = linkRepository.findByUserIdForUpdate(userId);
        if (link.isEmpty()) {
            return true;
        }
        if (!link.get().getEncryptedRefreshToken().equals(snapshotToken)) {
            return false;
        }
        linkRepository.deleteByUserIdInBulk(userId);
        tripCalendarSupport.clearAll(userId);
        return true;
    }

    private Duration backoffDelay(int exponent) {
        return properties.backoffBase().multipliedBy(1L << Math.max(0, Math.min(exponent, MAX_BACKOFF_EXPONENT)));
    }
}
