package com.jkymhs.backend.domain.trip.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TripTargetsResponse(List<Target> items, Long defaultTripId) {
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
