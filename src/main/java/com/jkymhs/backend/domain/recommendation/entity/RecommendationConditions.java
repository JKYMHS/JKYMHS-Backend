package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.time.LocalTime;

public record RecommendationConditions(
        Long sidoRegionId,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        String originName,
        String originAddress,
        double originLat,
        double originLng,
        Companion companion,
        ChildAgeGroup childAgeGroup,
        boolean indoor,
        Long targetTripId
) {
}
