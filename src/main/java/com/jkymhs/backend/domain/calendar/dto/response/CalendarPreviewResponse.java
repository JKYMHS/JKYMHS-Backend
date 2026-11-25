package com.jkymhs.backend.domain.calendar.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.trip.service.CalendarState;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CalendarPreviewResponse(
        Long tripId,
        Long planId,
        String planName,
        String calendarId,
        List<Day> days
) {
    public record Day(int dayNumber, LocalDate date, List<Item> items) {
    }

    public record Item(
            Long itemId,
            String name,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Integer durationMinutes,
            boolean allDay,
            CalendarState state
    ) {
    }
}
