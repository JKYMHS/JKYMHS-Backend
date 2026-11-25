package com.jkymhs.backend.domain.trip.event;

import java.util.List;

public record CalendarEventsOrphanedEvent(Long userId, Long tripId, List<CalendarEventRef> events) {
}
