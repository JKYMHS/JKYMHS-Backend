package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.route.service.RouteResult;

import java.time.LocalTime;

/**
 * 일정안 초안의 장소 하나.
 *
 * @param sortOrder 그날 안의 순서 (저장할 때 1..n으로 다시 매긴다)
 * @param move      앞 장소(첫째 날 첫 장소는 출발지)에서 오는 이동. null이면 저장할 때 캐시·어림값으로 채운다 (그날 첫 장소는 비운다)
 */
public record PlanItemDraft(
        Long contentId,
        int dayNumber,
        int sortOrder,
        LocalTime startTime,
        LocalTime endTime,
        Integer stayMinutes,
        String aiReason,
        boolean isRequired,
        RouteResult move
) {
}
