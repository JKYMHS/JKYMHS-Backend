package com.jkymhs.backend.domain.calendar.dto.response;

import java.util.List;

public record CalendarListResponse(List<Calendar> calendars) {
    public record Calendar(String id, String name, boolean primary, boolean writable) {
    }
}
