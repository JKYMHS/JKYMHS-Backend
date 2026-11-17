package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobRepository;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;
import com.jkymhs.backend.domain.trip.event.CalendarEventRef;
import com.jkymhs.backend.domain.trip.service.CalendarItem;
import com.jkymhs.backend.domain.trip.service.CalendarTrip;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 서버가 스스로 만드는 캘린더 작업 (확정안 수정·교체, 일정 삭제). 연동이 ACTIVE일 때만 만든다
 * — 만료(REVOKED)·해제 상태에서는 구글에 접근할 수 없어서, 다시 연동한 뒤 사용자가 업로드로 맞춘다.
 *
 * 메서드는 부르는 쪽 트랜잭션에 참여한다 (일정안 저장·삭제가 롤백되면 작업도 같이 사라지게).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CalendarAutoSyncService {

    private final GoogleCalendarLinkRepository linkRepository;
    private final CalendarSyncJobRepository jobRepository;
    private final CalendarJobService jobService;
    private final TripCalendarSupport tripCalendarSupport;

    /**
     * 확정안이 바뀌었다(저장·장소 담기·여행 날짜 변경). 캘린더에 올린 여행이면 SYNC 작업을 넣는다.
     * - 올라간 항목 중 바뀐 것(DIRTY)·실패한 것 → 고치기
     * - 아직 안 올라간 항목(NONE) → 새로 만들기 (한 번이라도 올린 여행이면 새로 담은 장소도 캘린더에 따라 올라가야 해서)
     * 이 여행에 진행 중인 작업이 있으면 만들지 않는다. 그 작업이 끝날 때 작업자가 이 메서드를 다시 불러서 남은 DIRTY를 맞춘다.
     *
     * @return 작업을 만들었으면 true
     */
    public boolean syncTrip(Long userId, Long tripId) {
        Optional<GoogleCalendarLink> link = activeLink(userId);
        if (link.isEmpty() || jobRepository.existsByTripIdAndStatusIn(tripId, CalendarJobStatus.ACTIVE)) {
            return false;
        }
        Optional<CalendarTrip> found = tripCalendarSupport.tripById(tripId);
        if (found.isEmpty() || !found.get().confirmed() || !found.get().hasEvents()) {
            return false;
        }
        CalendarTrip trip = found.get();
        String calendarId = calendarOf(trip, link.get());
        List<CalendarJobOp> ops = new ArrayList<>();
        for (CalendarItem item : trip.items()) {
            if (item.hasEvent() && item.syncStatus() != CalendarSyncStatus.SYNCED) {
                ops.add(CalendarJobOp.upsert(item, item.calendarId()));
            } else if (!item.hasEvent() && item.syncStatus() != CalendarSyncStatus.FAILED) {
                // 실패한 새 항목은 자동으로 다시 올리지 않는다 (사용자가 "다시 시도"로 올린다)
                ops.add(CalendarJobOp.upsert(item, calendarId));
            }
        }
        if (ops.isEmpty()) {
            return false;
        }
        jobService.create(userId, tripId, CalendarJobType.SYNC, calendarId, ops);
        return true;
    }

    /**
     * 확정안이 다른 일정안으로 바뀌었다. 예전 확정안의 일정을 지우고 새 확정안을 올린다 (예전 확정안을 캘린더에 올렸던 경우만).
     * 진행 중인 작업이 있어도 만든다 — 지울 일정 목록은 지금(이 이벤트)만 알 수 있어서 미룰 수 없다.
     */
    public void switchPlan(Long userId, Long tripId, Long newPlanId, List<CalendarEventRef> oldEvents) {
        Optional<GoogleCalendarLink> link = activeLink(userId);
        if (link.isEmpty() || oldEvents.isEmpty()) {
            return;
        }
        Optional<CalendarTrip> trip = tripCalendarSupport.tripById(tripId);
        if (trip.isEmpty()) {
            return;
        }
        String calendarId = calendarOf(trip.get(), link.get());
        List<CalendarJobOp> ops = new ArrayList<>(oldEvents.stream().map(ref -> CalendarJobOp.delete(ref, tripId)).toList());
        for (CalendarItem item : tripCalendarSupport.planItems(tripId, newPlanId)) {
            if (!(item.hasEvent() && item.syncStatus() == CalendarSyncStatus.SYNCED)) {
                ops.add(CalendarJobOp.upsert(item, item.hasEvent() ? item.calendarId() : calendarId));
            }
        }
        jobService.create(userId, tripId, CalendarJobType.SYNC, calendarId, ops);
    }

    /** 일정 항목이 지워진다 (여행·일정안 삭제, 저장에서 빠짐, 기간 축소). 구글에서 그 일정들을 지우는 작업 */
    public void deleteEvents(Long userId, Long tripId, List<CalendarEventRef> events) {
        if (events.isEmpty() || activeLink(userId).isEmpty()) {
            return;
        }
        jobService.create(userId, tripId, CalendarJobType.DELETE_EVENTS, null,
                events.stream().map(ref -> CalendarJobOp.delete(ref, tripId)).toList());
    }

    private Optional<GoogleCalendarLink> activeLink(Long userId) {
        return linkRepository.findByUserId(userId).filter(GoogleCalendarLink::isActive);
    }

    private static String calendarOf(CalendarTrip trip, GoogleCalendarLink link) {
        return StringUtils.hasText(trip.calendarId()) ? trip.calendarId() : link.getDefaultCalendarId();
    }
}
