package com.jkymhs.backend.domain.recommendation.dto.response;

public record RecommendationSaveResponse(Long tripId, Long planId, boolean isConfirmed) {
}
