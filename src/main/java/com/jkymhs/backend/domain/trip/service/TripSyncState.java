package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.global.web.CodeEnum;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 여행 단위 캘린더 상태 (확정 일정안 기준). 올린 항목이 하나도 없으면 none,
 * 바뀌었거나 기다리거나 실패한 항목이 하나라도 있으면 changed, 아니면 synced.
 */
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
