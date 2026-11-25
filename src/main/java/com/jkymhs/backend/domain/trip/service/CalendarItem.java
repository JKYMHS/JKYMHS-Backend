package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarItem(
        Long itemId,
        Long tripId,
        Long planId,
        Long contentId,
        String name,
        String address,
        String memo,
        int dayNumber,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer stayMinutes,
        String calendarId,
        String eventId,
        CalendarSyncStatus syncStatus,
        CalendarState state,
        String companion
) {
    public boolean hasEvent() {
        return eventId != null;
    }

    public boolean allDay() {
        return startTime == null;
    }
}
