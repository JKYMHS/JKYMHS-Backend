package com.jkymhs.backend.domain.trip.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * "여행에 담기" 창의 여행 목록 (다가오는 여행만, 시작일 순).
 *
 * @param defaultTripId 먼저 골라 둘 여행: 축제 기간과 겹치는 첫 여행 → 날짜가 같은 첫 여행 → 없으면 null
 */
public record TripTargetsResponse(List<Target> items, Long defaultTripId) {

    /**
     * @param overlaps    축제 기간이 여행 기간과 겹친다 (축제가 아니면 false)
     * @param dateMatched 탐색에서 고른 날짜와 시작·종료일이 같다
     * @param selectable  여행 일수가 고른 날짜의 일수 이상이다 (날짜를 안 보냈으면 true)
     */
    public record Target(
            Long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int planCount,
            Long confirmedPlanId,
            List<Plan> plans,
            List<Day> days,
            boolean overlaps,
            boolean dateMatched,
            boolean selectable
    ) {
    }

    public record Plan(Long id, String name, boolean isConfirmed) {
    }

    public record Day(int dayNumber, LocalDate date) {
    }
}
