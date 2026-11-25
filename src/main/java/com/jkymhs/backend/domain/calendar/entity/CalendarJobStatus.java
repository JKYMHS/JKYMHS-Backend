package com.jkymhs.backend.domain.calendar.entity;

import java.util.EnumSet;
import java.util.Set;

public enum CalendarJobStatus {
    PENDING,
    RUNNING,
    DONE,
    PARTIAL_FAILED,
    FAILED;

    public static final Set<CalendarJobStatus> ACTIVE = EnumSet.of(PENDING, RUNNING);

    public boolean terminal() {
        return !ACTIVE.contains(this);
    }
}
