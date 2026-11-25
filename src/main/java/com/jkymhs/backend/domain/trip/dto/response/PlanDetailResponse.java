package com.jkymhs.backend.domain.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.route.service.MoveMode;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.service.CalendarState;
import com.jkymhs.backend.domain.trip.service.PlanWarning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlanDetailResponse(
        Long id,
        Long tripId,
        String name,
        PlanSource source,
        boolean isConfirmed,
        int version,
        int totalMoveMinutes,
        OriginResponse origin,
        List<Day> days
) {
    public record Day(int dayNumber, LocalDate date, int placeCount, int warningCount, List<Item> items) {
    }

    public record Item(
            Long itemId,
            Long contentId,
            String name,
            String address,
            String imageUrl,
            String category,
            double lat,
            double lng,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Integer stayMinutes,
            String memo,
            String aiReason,
            boolean isRequired,
            boolean contentDeleted,
            Move move,
            List<PlanWarning> warnings,
            CalendarState calendarState
    ) {
    }

    public record Move(
            MoveMode mode,
            Integer minutes,
            MoveMode altMode,
            Integer altMinutes,
            String summary,
            String polyline,
            boolean isEstimated
    ) {
    }
}
