package com.jkymhs.backend.domain.route.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "route")
public record RouteProperties(
        @DefaultValue("7d") @NotNull Duration cacheTtl,
        @DefaultValue("1000") @Min(0) int walkThresholdMeters,
        @DefaultValue("500") @Min(0) int slowWalkThresholdMeters,
        @DefaultValue("1.5") @DecimalMin("1.0") double transitRatio,
        @Valid @DefaultValue Executor executor
) {
    public record Executor(
            @DefaultValue("4") @Min(1) int coreSize,
            @DefaultValue("8") @Min(1) int maxSize,
            @DefaultValue("200") @Min(0) int queueCapacity
    ) {
    }
}
