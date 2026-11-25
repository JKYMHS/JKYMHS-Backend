package com.jkymhs.backend.domain.trip.service;

import java.time.LocalDate;
import java.util.List;

public record CalendarTrip(
        Long tripId,
        Long userId,
        String title,
        Long planId,
        String planName,
        String calendarId,
        LocalDate startDate,
        int days,
        String companion,
        List<CalendarItem> items
) {
    public boolean confirmed() {
        return planId != null;
    }

    public boolean hasEvents() {
        return items.stream().anyMatch(CalendarItem::hasEvent);
    }
}
