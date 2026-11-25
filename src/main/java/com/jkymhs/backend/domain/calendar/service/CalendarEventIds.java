package com.jkymhs.backend.domain.calendar.service;

public final class CalendarEventIds {
    private static final String PREFIX = "edgl";

    private CalendarEventIds() {
    }

    public static String of(Long planItemId) {
        return PREFIX + planItemId;
    }

    public static Long planItemIdOf(String eventId) {
        if (eventId == null || !eventId.startsWith(PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(eventId.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
