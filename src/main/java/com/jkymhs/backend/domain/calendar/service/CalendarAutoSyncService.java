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

@Service
@RequiredArgsConstructor
@Transactional
public class CalendarAutoSyncService {
    private final GoogleCalendarLinkRepository linkRepository;
    private final CalendarSyncJobRepository jobRepository;
    private final CalendarJobService jobService;
    private final TripCalendarSupport tripCalendarSupport;

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
                ops.add(CalendarJobOp.upsert(item, calendarId));
            }
        }
        if (ops.isEmpty()) {
            return false;
        }
        jobService.create(userId, tripId, CalendarJobType.SYNC, calendarId, ops);
        return true;
    }

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
