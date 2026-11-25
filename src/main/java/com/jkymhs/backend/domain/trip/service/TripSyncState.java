package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.global.web.CodeEnum;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

public enum TripSyncState implements CodeEnum {
    SYNCED("synced"),
    CHANGED("changed"),
    NONE("none");

    private static final Set<CalendarSyncStatus> CHANGED_STATUSES =
            EnumSet.of(CalendarSyncStatus.DIRTY, CalendarSyncStatus.PENDING, CalendarSyncStatus.FAILED);

    private final String code;

    TripSyncState(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static TripSyncState of(Collection<PlanItem> confirmedItems) {
        if (confirmedItems == null || confirmedItems.stream().noneMatch(PlanItem::hasCalendarEvent)) {
            return NONE;
        }
        if (confirmedItems.stream().anyMatch(item -> CHANGED_STATUSES.contains(item.getSyncStatus()))) {
            return CHANGED;
        }
        return SYNCED;
    }
}
