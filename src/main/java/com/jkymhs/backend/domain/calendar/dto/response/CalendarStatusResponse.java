package com.jkymhs.backend.domain.calendar.dto.response;

import com.jkymhs.backend.domain.calendar.entity.CalendarLinkStatus;
import com.jkymhs.backend.domain.trip.service.TripSyncState;

import java.time.LocalDateTime;
import java.util.List;

public record CalendarStatusResponse(
        boolean linked,
        CalendarLinkStatus status,
        String googleEmail,
        LocalDateTime linkedAt,
        String defaultCalendarId,
        long uploadedEventCount,
        List<Trip> trips
) {
    public record Trip(Long tripId, String title, TripSyncState sync) {
    }
}
