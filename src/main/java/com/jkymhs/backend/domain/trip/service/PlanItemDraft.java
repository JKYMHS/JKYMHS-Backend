package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.route.service.RouteResult;

import java.time.LocalTime;

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
