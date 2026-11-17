package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;
import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.repository.PlanItemRepository;
import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 캘린더 도메인이 여행·일정 항목을 읽고 캘린더 상태를 바꾸는 유일한 창구.
 *
 * 캘린더 도메인은 여행 도메인의 Repository를 직접 쓰지 않는다 (다른 도메인 데이터는 그 도메인 Service로만, 컨벤션 §1).
 * 이 클래스가 여행 패키지 안에 있어서 일정 항목 엔티티의 캘린더 의도 메서드(markSynced 등)를 부를 수 있고,
 * 캘린더 도메인에는 엔티티 대신 {@link CalendarTrip}·{@link CalendarItem} 값만 넘긴다.
 *
 * 쓰기 메서드는 부르는 쪽 트랜잭션에 참여한다 (캘린더 작업 행을 넣는 것과 항목을 PENDING으로 바꾸는 것이 같이 커밋·롤백되게).
 * 동기화 작업자가 부를 때는 그 사이에 항목이 지워졌을 수 있어서, 없는 항목은 조용히 넘어간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripCalendarSupport {

    private static final String NOT_MY_TRIP = "올릴 수 없는 여행이 포함되어 있습니다.";

    private final TripAccess tripAccess;
    private final TripRepository tripRepository;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final Clock clock;

    // ---------- 조회 ----------

    /**
     * 업로드 미리보기·업로드: 내 여행과 확정 일정안.
     *
     * @throws CustomException 404 없는 여행 · 403 다른 회원의 여행 · 400 PLAN_NOT_CONFIRMED 확정안 없음
     */
    public CalendarTrip confirmedTrip(Long userId, Long tripId) {
        Trip trip = tripAccess.ownedTrip(userId, tripId);
        if (trip.getConfirmedPlanId() == null) {
            throw new CustomException(ErrorCode.PLAN_NOT_CONFIRMED, "확정안 없는 여행: " + tripId);
        }
        return view(trip, trip.getConfirmedPlanId());
    }

    /**
     * 일괄 업로드: 여러 여행을 한 번에 고르는 요청이라, 없거나 남의 여행이면 403/404가 아니라 요청 오류(400)로 본다.
     * 확정안이 없는 여행은 planId가 null인 값으로 돌려준다 (부르는 쪽이 "확정 전"으로 건너뛴다).
     */
    public CalendarTrip tripForBulk(Long userId, Long tripId) {
        Trip trip = tripRepository.findById(tripId).filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT, NOT_MY_TRIP,
                        "없거나 다른 회원의 여행: " + tripId));
        return view(trip, trip.getConfirmedPlanId());
    }

    /** 자동 동기화: 주인 확인 없이 여행과 확정안을 읽는다. 여행이 없으면 빈 값 */
    public Optional<CalendarTrip> tripById(Long tripId) {
        return tripRepository.findById(tripId).map(trip -> view(trip, trip.getConfirmedPlanId()));
    }

    /** 이 여행의 특정 일정안 항목들 (확정안이 바뀌는 순간에는 아직 trips.confirmed_plan_id가 예전 값이라 id로 읽는다) */
    public List<CalendarItem> planItems(Long tripId, Long planId) {
        Optional<Trip> trip = tripRepository.findById(tripId);
        Optional<Plan> plan = planRepository.findById(planId).filter(found -> found.getTripId().equals(tripId));
        if (trip.isEmpty() || plan.isEmpty()) {
            return List.of();
        }
        return items(trip.get(), planId);
    }

    /** 동기화 작업자가 일정을 만들 때: 지금(작업 시점) 항목 값. 그사이 지워졌으면 빈 값 */
    public Optional<CalendarItem> item(Long planItemId) {
        return planItemRepository.findById(planItemId).flatMap(item -> planRepository.findById(item.getPlanId())
                .flatMap(plan -> tripRepository.findById(plan.getTripId()))
                .map(trip -> toView(trip, item)));
    }

    /** 이 회원이 캘린더에 올린 모든 항목 (확정안이 아닌 일정안에 남은 것까지). 해제·탈퇴 때 지울 목록 */
    public List<CalendarItem> uploadedItems(Long userId) {
        Map<Long, Trip> trips = tripsOf(userId);
        if (trips.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> tripOfPlan = tripOfPlan(trips.keySet());
        if (tripOfPlan.isEmpty()) {
            return List.of();
        }
        return planItemRepository.findAllByPlanIdInAndCalendarEventIdIsNotNull(tripOfPlan.keySet()).stream()
                .sorted(Comparator.comparing(PlanItem::getId))
                .map(item -> toView(trips.get(tripOfPlan.get(item.getPlanId())), item))
                .toList();
    }

    /**
     * 캘린더에 일정이 하나라도 있는 여행들 (연동 화면 목록). 상태는 확정안 기준이다 ({@link TripSyncState}).
     * 시작일 순.
     */
    public List<TripSyncView> syncedTrips(Long userId) {
        Map<Long, Trip> trips = tripsOf(userId);
        if (trips.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> tripOfPlan = tripOfPlan(trips.keySet());
        if (tripOfPlan.isEmpty()) {
            return List.of();
        }
        List<Long> tripIdsWithEvents = planItemRepository.findAllByPlanIdInAndCalendarEventIdIsNotNull(tripOfPlan.keySet())
                .stream().map(item -> tripOfPlan.get(item.getPlanId())).distinct().toList();
        List<Long> confirmedPlanIds = tripIdsWithEvents.stream().map(id -> trips.get(id).getConfirmedPlanId())
                .filter(Objects::nonNull).toList();
        Map<Long, List<PlanItem>> confirmedItems = confirmedPlanIds.isEmpty() ? Map.of()
                : planItemRepository.findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(confirmedPlanIds)
                .stream().collect(Collectors.groupingBy(PlanItem::getPlanId));
        return tripIdsWithEvents.stream().map(trips::get)
                .sorted(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId))
                .map(trip -> new TripSyncView(trip.getId(), trip.getTitle(),
                        TripSyncState.of(confirmedItems.getOrDefault(trip.getConfirmedPlanId(), List.of()))))
                .toList();
    }

    /** 작업 상태 화면의 여행 이름. 지워진 여행은 빠진다 */
    public Map<Long, String> titles(Collection<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        return tripRepository.findAllById(tripIds).stream()
                .collect(Collectors.toMap(Trip::getId, Trip::getTitle));
    }

    // ---------- 캘린더 상태 바꾸기 ----------

    /** 동기화 작업에 넣은 항목: 화면에 "기다리는 중"(new/changed)으로 보인다 */
    @Transactional
    public void markPending(Collection<Long> itemIds) {
        if (!itemIds.isEmpty()) {
            planItemRepository.findAllById(itemIds).forEach(PlanItem::markPending);
        }
    }

    @Transactional
    public void markSynced(Long itemId, String calendarId, String eventId) {
        LocalDateTime now = LocalDateTime.now(clock);
        planItemRepository.findById(itemId).ifPresent(item -> item.markSynced(calendarId, eventId, now));
    }

    @Transactional
    public void markFailed(Long itemId) {
        planItemRepository.findById(itemId).ifPresent(PlanItem::markFailed);
    }

    /**
     * 구글 캘린더에서 일정을 지웠다. 항목이 아직 그 일정을 가리킬 때만 비운다
     * (지우는 사이에 같은 항목을 다시 올렸으면 새 일정 정보를 지우면 안 된다).
     */
    @Transactional
    public void clearEvent(Long itemId, String calendarId, String eventId) {
        planItemRepository.findById(itemId)
                .filter(item -> eventId.equals(item.getCalendarEventId()) && Objects.equals(calendarId, item.getCalendarId()))
                .ifPresent(PlanItem::clearCalendar);
    }

    /** 연동을 끊었다: 이 회원의 모든 항목·여행에서 캘린더 정보를 지운다 (구글 쪽 일정은 그대로 남는다) */
    @Transactional
    public void clearAll(Long userId) {
        Map<Long, Trip> trips = tripsOf(userId);
        if (trips.isEmpty()) {
            return;
        }
        trips.values().forEach(trip -> trip.useCalendar(null));
        Map<Long, Long> tripOfPlan = tripOfPlan(trips.keySet());
        if (!tripOfPlan.isEmpty()) {
            // 올린 일정뿐 아니라 기다리는 중(PENDING)·실패(FAILED)로 남은 항목도 "올린 적 없음"으로 되돌린다
            planItemRepository.findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(tripOfPlan.keySet()).stream()
                    .filter(item -> item.hasCalendarEvent() || item.getSyncStatus() != CalendarSyncStatus.NONE)
                    .forEach(PlanItem::clearCalendar);
        }
    }

    /** 업로드할 때 고른 캘린더를 여행에 기억한다 (다음 업로드 화면의 기본값) */
    @Transactional
    public void useCalendar(Long tripId, String calendarId) {
        tripRepository.findById(tripId).ifPresent(trip -> trip.useCalendar(calendarId));
    }

    // ---------- 내부 ----------

    private Map<Long, Trip> tripsOf(Long userId) {
        List<Long> ids = tripRepository.findIdsByUserId(userId);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Trip> trips = new LinkedHashMap<>();
        tripRepository.findAllById(ids).forEach(trip -> trips.put(trip.getId(), trip));
        return trips;
    }

    // planId → tripId
    private Map<Long, Long> tripOfPlan(Collection<Long> tripIds) {
        return planRepository.findIdAndTripIdByTripIdIn(tripIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private CalendarTrip view(Trip trip, Long planId) {
        if (planId == null) {
            return new CalendarTrip(trip.getId(), trip.getUserId(), trip.getTitle(), null, null, trip.getCalendarId(),
                    trip.getStartDate(), trip.days(), trip.getCompanion().code(), List.of());
        }
        String planName = planRepository.findById(planId).map(Plan::getName).orElse(null);
        return new CalendarTrip(trip.getId(), trip.getUserId(), trip.getTitle(), planId, planName, trip.getCalendarId(),
                trip.getStartDate(), trip.days(), trip.getCompanion().code(), items(trip, planId));
    }

    private List<CalendarItem> items(Trip trip, Long planId) {
        return planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(planId).stream()
                .map(item -> toView(trip, item)).toList();
    }

    private static CalendarItem toView(Trip trip, PlanItem item) {
        return new CalendarItem(item.getId(), trip.getId(), item.getPlanId(), item.getContentId(), item.getSnapshotName(),
                item.getSnapshotAddress(), item.getMemo(), item.getDayNumber(), trip.dateOf(item.getDayNumber()),
                item.getStartTime(), item.getEndTime(), item.getStayMinutes(), item.getCalendarId(),
                item.getCalendarEventId(), item.getSyncStatus(), CalendarState.of(item), trip.getCompanion().code());
    }
}
