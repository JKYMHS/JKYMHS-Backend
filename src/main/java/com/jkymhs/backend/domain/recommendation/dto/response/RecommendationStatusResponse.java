package com.jkymhs.backend.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.recommendation.entity.FailureReason;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStep;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public sealed interface RecommendationStatusResponse {
    record Pending(
            Long requestId,
            RecommendationStatus status,
            RecommendationStep step,
            int stepIndex,
            int stepTotal,
            long pollAfterMs,
            Progress progress
    ) implements RecommendationStatusResponse {
    }

    record Progress(int candidateCount, int selectedCount) {
    }

    record Result(
            Long requestId,
            RecommendationStatus status,
            boolean isFallback,
            boolean indoorShortage,
            Long previousRequestId,
            LocalDateTime expiresAt,
            Conditions conditions,
            List<CourseResponse> courses
    ) implements RecommendationStatusResponse {
    }

    record Conditions(
            Long regionId,
            String regionName,
            List<Long> districtIds,
            List<String> districtNames,
            LocalDate startDate,
            LocalDate endDate,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            Companion companion,
            ChildAgeGroup childAgeGroup,
            boolean indoor,
            Origin origin
    ) {
    }

    record Origin(String name, String address, Double lat, Double lng) {
    }

    record Failed(Long requestId, RecommendationStatus status, boolean refunded, FailureReason reason)
            implements RecommendationStatusResponse {
    }
}
