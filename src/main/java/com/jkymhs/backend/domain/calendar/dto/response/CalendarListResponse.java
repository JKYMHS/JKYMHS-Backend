package com.jkymhs.backend.domain.calendar.dto.response;

import java.util.List;

/**
 * 일정을 쓸 수 있는 캘린더만 (accessRole owner·writer). writable은 FE 타입을 맞추려고 늘 true로 내려간다.
 */
public record CalendarListResponse(List<Calendar> calendars) {

    public record Calendar(String id, String name, boolean primary, boolean writable) {
    }
}
