package com.jkymhs.backend.domain.trip.event;

import java.util.List;

public record ConfirmedPlanSwitchedEvent(Long userId, Long tripId, Long oldPlanId, Long newPlanId,
                                         List<CalendarEventRef> oldEvents) {
}
