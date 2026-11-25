package com.jkymhs.backend.domain.recommendation.dto.request;

import java.util.List;

public record RecommendationRetryRequest(List<Long> excludeContentIds) {
    public static final RecommendationRetryRequest EMPTY = new RecommendationRetryRequest(List.of());

    public List<Long> excludeIds() {
        return excludeContentIds == null ? List.of() : excludeContentIds.stream().filter(id -> id != null).toList();
    }
}
