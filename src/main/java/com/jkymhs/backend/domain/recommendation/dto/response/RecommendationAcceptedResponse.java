package com.jkymhs.backend.domain.recommendation.dto.response;

import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;

import java.time.LocalDate;
import java.util.List;

public record RecommendationAcceptedResponse(
        Long requestId,
        Long previousRequestId,
        RecommendationStatus status,
        int left,
        long pollAfterMs,
        List<Warning> warnings
) {
    public record Warning(Long contentId, String code, LocalDate availableFrom, LocalDate availableTo) {
    }
}
