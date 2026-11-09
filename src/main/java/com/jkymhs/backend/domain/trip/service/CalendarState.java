package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 화면에 보여주는 일정 항목의 캘린더 상태 (FE badges: 새 일정 / 변경됨 / 동기화됨 / 실패).
 * DB의 {@link CalendarSyncStatus}를 이렇게 바꾼다: NONE → new, DIRTY → changed,
 * PENDING → 올린 적 없으면 new·있으면 changed, SYNCED → synced, FAILED → failed.
 */
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
