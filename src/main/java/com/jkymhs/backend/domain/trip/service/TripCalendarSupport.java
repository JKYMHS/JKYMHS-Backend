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

    public CalendarTrip confirmedTrip(Long userId, Long tripId) {
        Trip trip = tripAccess.ownedTrip(userId, tripId);
        if (trip.getConfirmedPlanId() == null) {
            throw new CustomException(ErrorCode.PLAN_NOT_CONFIRMED, "확정안 없는 여행: " + tripId);
        }
        return view(trip, trip.getConfirmedPlanId());
    }

    public CalendarTrip tripForBulk(Long userId, Long tripId) {
        Trip trip = tripRepository.findById(tripId).filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT, NOT_MY_TRIP,
                        "없거나 다른 회원의 여행: " + tripId));
        return view(trip, trip.getConfirmedPlanId());
    }

    public Optional<CalendarTrip> tripById(Long tripId) {
        return tripRepository.findById(tripId).map(trip -> view(trip, trip.getConfirmedPlanId()));
    }

    public List<CalendarItem> planItems(Long tripId, Long planId) {
        Optional<Trip> trip = tripRepository.findById(tripId);
        Optional<Plan> plan = planRepository.findById(planId).filter(found -> found.getTripId().equals(tripId));
        if (trip.isEmpty() || plan.isEmpty()) {
            return List.of();
        }
        return items(trip.get(), planId);
    }

    public Optional<CalendarItem> item(Long planItemId) {
        return planItemRepository.findById(planItemId).flatMap(item -> planRepository.findById(item.getPlanId())
                .flatMap(plan -> tripRepository.findById(plan.getTripId()))
                .map(trip -> toView(trip, item)));
    }

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

    public Map<Long, String> titles(Collection<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        return tripRepository.findAllById(tripIds).stream()
                .collect(Collectors.toMap(Trip::getId, Trip::getTitle));
    }

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

    @Transactional
    public void clearEvent(Long itemId, String calendarId, String eventId) {
        planItemRepository.findById(itemId)
                .filter(item -> eventId.equals(item.getCalendarEventId()) && Objects.equals(calendarId, item.getCalendarId()))
                .ifPresent(PlanItem::clearCalendar);
    }

    @Transactional
    public void clearAll(Long userId) {
        Map<Long, Trip> trips = tripsOf(userId);
        if (trips.isEmpty()) {
            return;
        }
        trips.values().forEach(trip -> trip.useCalendar(null));
        Map<Long, Long> tripOfPlan = tripOfPlan(trips.keySet());
        if (!tripOfPlan.isEmpty()) {
            planItemRepository.findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(tripOfPlan.keySet()).stream()
                    .filter(item -> item.hasCalendarEvent() || item.getSyncStatus() != CalendarSyncStatus.NONE)
                    .forEach(PlanItem::clearCalendar);
        }
    }

    @Transactional
    public void useCalendar(Long tripId, String calendarId) {
        tripRepository.findById(tripId).ifPresent(trip -> trip.useCalendar(calendarId));
    }

    private Map<Long, Trip> tripsOf(Long userId) {
        List<Long> ids = tripRepository.findIdsByUserId(userId);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Trip> trips = new LinkedHashMap<>();
        tripRepository.findAllById(ids).forEach(trip -> trips.put(trip.getId(), trip));
        return trips;
    }

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
