package com.jkymhs.backend.domain.trip.event;

/**
 * 구글 캘린더에 올라간 일정 하나 (어느 캘린더의 어느 일정인지).
 */
public record CalendarEventRef(String calendarId, String eventId) {
}
