package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.global.web.CodeEnum;

public enum CalendarState implements CodeEnum {
    NEW("new"),
    CHANGED("changed"),
    SYNCED("synced"),
    FAILED("failed");

    private final String code;

    CalendarState(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static CalendarState of(PlanItem item) {
        return switch (item.getSyncStatus()) {
            case NONE -> NEW;
            case DIRTY -> CHANGED;
            case PENDING -> item.hasCalendarEvent() ? CHANGED : NEW;
            case SYNCED -> SYNCED;
            case FAILED -> FAILED;
        };
    }
}
