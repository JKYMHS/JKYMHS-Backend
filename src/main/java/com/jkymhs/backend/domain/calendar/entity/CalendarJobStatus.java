package com.jkymhs.backend.domain.calendar.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 캘린더 작업 상태. PENDING(기다림) → RUNNING(작업자가 처리 중) → DONE / PARTIAL_FAILED / FAILED.
 * 한 번에 다 못 끝내면(항목이 많거나 구글이 잠깐 거절) 다시 PENDING으로 돌아가 다음 차례를 기다린다.
 */
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
