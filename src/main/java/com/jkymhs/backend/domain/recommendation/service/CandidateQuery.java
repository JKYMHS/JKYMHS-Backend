package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.user.entity.TravelStyle;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record CandidateQuery(
        Long userId,
        Long sidoId,
        List<Long> districtIds,
        LocalDate startDate,
        LocalDate endDate,
        Companion companion,
        boolean indoor,
        Set<TravelStyle> styles,
        boolean pet,
        List<Long> requiredIds,
        Set<Long> penalized
) {
}
