package com.jkymhs.backend.domain.trip.event;

public record ConfirmedPlanChangedEvent(Long userId, Long tripId, Long planId) {
}
