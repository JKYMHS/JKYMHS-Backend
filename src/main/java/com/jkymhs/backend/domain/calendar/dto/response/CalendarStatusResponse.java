package com.jkymhs.backend.domain.calendar.dto.response;

import com.jkymhs.backend.domain.calendar.entity.CalendarLinkStatus;
import com.jkymhs.backend.domain.trip.service.TripSyncState;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 캘린더 연동 화면.
 *
 * @param linked             연동 행이 있으면 true (REVOKED도 true — 화면이 "다시 연동해주세요"를 보여준다)
 * @param status             ACTIVE / REVOKED, 연동이 없으면 null
 * @param uploadedEventCount 지금 구글 캘린더에 올라가 있는 일정 수
 * @param trips              일정이 하나라도 올라간 여행 (상태는 확정안 기준)
 */
public record CalendarStatusResponse(
        boolean linked,
        CalendarLinkStatus status,
        String googleEmail,
        LocalDateTime linkedAt,
        String defaultCalendarId,
        long uploadedEventCount,
        List<Trip> trips
) {

    public record Trip(Long tripId, String title, TripSyncState sync) {
    }
}
