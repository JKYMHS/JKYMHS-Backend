package com.jkymhs.backend.domain.recommendation.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "recommendation")
public record RecommendationProperties(
        @DefaultValue("10") @Min(1) int dailyLimit,
        @DefaultValue("24h") @NotNull Duration resultTtl,
        @DefaultValue("1h") @NotNull Duration candidateCacheTtl,
        @DefaultValue("200") @Min(1) int candidatePoolSize,
        @DefaultValue("40") @Min(1) int candidateLimit,
        @DefaultValue("25s") @NotNull Duration deadline,
        @DefaultValue("10s") @NotNull Duration dedupeWindow,
        @DefaultValue("10m") @NotNull Duration pendingTimeout,
        @DefaultValue("24h") @NotNull Duration originRetention,
        @Valid @DefaultValue Executor executor
) {
    public record Executor(
            @DefaultValue("4") @Min(1) int coreSize,
            @DefaultValue("8") @Min(1) int maxSize,
            @DefaultValue("50") @Min(0) int queueCapacity
    ) {
    }
}
