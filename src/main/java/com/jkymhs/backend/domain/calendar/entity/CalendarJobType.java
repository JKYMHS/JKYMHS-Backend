package com.jkymhs.backend.domain.calendar.entity;

import java.util.EnumSet;
import java.util.Set;

public enum CalendarJobType {
    UPLOAD,
    BULK_UPLOAD,
    SYNC,
    MOVE,
    DELETE_EVENTS,
    DELETE_ALL;

    public static final Set<CalendarJobType> USER_INITIATED = EnumSet.of(UPLOAD, BULK_UPLOAD);

    public boolean userInitiated() {
        return USER_INITIATED.contains(this);
    }
}
