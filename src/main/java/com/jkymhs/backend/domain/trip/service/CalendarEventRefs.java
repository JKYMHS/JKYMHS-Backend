package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.event.CalendarEventRef;

import java.util.Collection;
import java.util.List;

final class CalendarEventRefs {
    private CalendarEventRefs() {
    }

    static List<CalendarEventRef> of(Collection<PlanItem> items) {
        return items.stream()
                .filter(PlanItem::hasCalendarEvent)
                .map(item -> new CalendarEventRef(item.getCalendarId(), item.getCalendarEventId()))
                .toList();
    }
}
