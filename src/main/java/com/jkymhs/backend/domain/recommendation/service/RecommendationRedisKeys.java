package com.jkymhs.backend.domain.recommendation.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.stream.Collectors;

public final class RecommendationRedisKeys {
    private RecommendationRedisKeys() {
    }

    public static String candidates(Long sidoId, Collection<Long> districtIds, LocalDate startDate, LocalDate endDate) {
        String districts = districtIds == null || districtIds.isEmpty() ? "all"
                : districtIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "candidates:" + sidoId + ":" + districts + ":" + startDate + ":" + endDate;
    }

    public static String result(Long requestId) {
        return "rec:result:" + requestId;
    }

    public static String progress(Long requestId) {
        return "rec:progress:" + requestId;
    }
}
