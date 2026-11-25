package com.jkymhs.backend.global.event;

import java.time.LocalDateTime;

public record BehaviorSignalEvent(
        Long userId,
        Long contentId,
        BehaviorSignalType type,
        String companion,
        Long tripId,
        LocalDateTime occurredAt
) {
}
