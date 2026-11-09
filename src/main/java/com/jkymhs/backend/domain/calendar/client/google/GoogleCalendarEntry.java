package com.jkymhs.backend.domain.calendar.client.google;

/**
 * 사용자의 캘린더 목록(calendarList) 한 줄.
 *
 * @param accessRole owner·writer면 일정을 쓸 수 있다. reader·freeBusyReader는 읽기만 된다
 */
public record GoogleCalendarEntry(String id, String name, boolean primary, String accessRole) {

    public boolean writable() {
        return "owner".equals(accessRole) || "writer".equals(accessRole);
    }
}
