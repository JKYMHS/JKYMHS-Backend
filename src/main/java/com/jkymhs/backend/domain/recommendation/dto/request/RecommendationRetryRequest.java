package com.jkymhs.backend.domain.recommendation.dto.request;

import java.util.List;

/**
 * @param excludeContentIds 다시 추천에서 빼고 싶은 장소 (점수를 크게 깎는다)
 */
public record RecommendationRetryRequest(List<Long> excludeContentIds) {

    public static final RecommendationRetryRequest EMPTY = new RecommendationRetryRequest(List.of());

    public List<Long> excludeIds() {
        return excludeContentIds == null ? List.of() : excludeContentIds.stream().filter(id -> id != null).toList();
    }
}
