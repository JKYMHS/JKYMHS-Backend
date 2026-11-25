package com.jkymhs.backend.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalTime;

public record RecommendationQuotaResponse(int total, int used, int left, String resetAt, Defaults defaults) {
    public record Defaults(Companion companion, @JsonFormat(pattern = "HH:mm") LocalTime startTime) {
    }
}
