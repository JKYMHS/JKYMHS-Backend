package com.jkymhs.backend.domain.trip.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 캘린더 도메인에 넘기는 여행 + 확정 일정안의 항목들.
 *
 * @param planId     확정 일정안 id. 확정안이 없으면 null이고 items는 비어 있다
 * @param calendarId 이 여행을 마지막으로 올린 캘린더 (올린 적 없으면 null)
 * @param companion  동행 FE 코드 (행동 데이터 신호에 넣는다)
 */
public record CalendarTrip(
        Long tripId,
        Long userId,
        String title,
        Long planId,
        String planName,
        String calendarId,
        LocalDate startDate,
        int days,
        String companion,
        List<CalendarItem> items
) {

    public boolean confirmed() {
        return planId != null;
    }

    /** 확정안 항목 중 하나라도 캘린더에 올라가 있는지 */
    public boolean hasEvents() {
        return items.stream().anyMatch(CalendarItem::hasEvent);
    }
}
