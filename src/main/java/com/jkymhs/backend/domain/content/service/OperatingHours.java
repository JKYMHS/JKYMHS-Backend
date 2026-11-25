package com.jkymhs.backend.domain.content.service;

import java.time.LocalTime;

public record OperatingHours(LocalTime open, LocalTime close, LocalTime lastEntry) {

    public static final OperatingHours UNKNOWN = new OperatingHours(null, null, null);
}
