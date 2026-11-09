package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.service.TripSyncState;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 여행 상세 (일정안 목록 포함, 일정안 id 순).
 */
public record TripDetailResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        List<Region> regions,
        Companion companion,
        ChildAgeGroup childAgeGroup,
        OriginResponse origin,
        Long confirmedPlanId,
        Calendar calendar,
        int version,
        List<PlanSummary> plans
) {

    /**
     * @param fullName "서울 중구" (시·도면 "서울")
     */
    public record Region(Long id, String name, String fullName) {
    }

    public record Calendar(String calendarId, TripSyncState sync) {
    }

    public record PlanSummary(
            Long id,
            String name,
            PlanSource source,
            boolean isConfirmed,
            int placeCount,
            int totalMoveMinutes,
            LocalDateTime createdAt
    ) {
    }
}
