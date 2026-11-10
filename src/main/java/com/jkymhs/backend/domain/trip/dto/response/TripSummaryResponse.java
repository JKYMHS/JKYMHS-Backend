package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.service.TripGroup;
import com.jkymhs.backend.domain.trip.service.TripSyncState;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 목록 카드 하나.
 *
 * @param confirmedPlan 확정 일정안. placeCount는 "캘린더에 일괄 올리기" 창의 일정 수로 쓴다
 * @param visit         끝난 여행 + 확정 일정안이 있을 때만 (방문 확인 n/m)
 * @param thumbnailUrl  확정 일정안(없으면 첫 일정안)의 첫 장소 사진
 */
public record TripSummaryResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TripGroup group,
        List<Region> regions,
        Companion companion,
        String originName,
        int planCount,
        ConfirmedPlan confirmedPlan,
        Visit visit,
        TripSyncState sync,
        String thumbnailUrl
) {

    public record Region(Long id, String name) {
    }

    public record ConfirmedPlan(Long id, String name, int placeCount) {
    }

    public record Visit(int checked, int total) {
    }
}
