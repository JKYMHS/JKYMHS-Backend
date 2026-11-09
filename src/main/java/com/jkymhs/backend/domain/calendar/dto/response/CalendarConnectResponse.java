package com.jkymhs.backend.domain.calendar.dto.response;

public record CalendarConnectResponse(boolean linked, String googleEmail, String defaultCalendarId) {
}
