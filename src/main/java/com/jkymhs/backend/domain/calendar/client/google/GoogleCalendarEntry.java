package com.jkymhs.backend.domain.calendar.client.google;

public record GoogleCalendarEntry(String id, String name, boolean primary, String accessRole) {
    public boolean writable() {
        return "owner".equals(accessRole) || "writer".equals(accessRole);
    }
}
