package com.jkymhs.backend.domain.behavior.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "behavior")
public record BehaviorProperties(
        @DefaultValue("30d") @NotNull Duration halfLife,
        @DefaultValue("180d") @NotNull Duration profileWindow,
        @DefaultValue("10") @Min(1) int companionMinSignals,
        @DefaultValue @Valid Executor executor
) {

    public BehaviorProperties {
        if (halfLife.isZero() || halfLife.isNegative()) {
            throw new IllegalArgumentException("behavior.half-life는 0보다 커야 합니다");
        }
    }

    public record Executor(
            @DefaultValue("2") @Min(1) int coreSize,
            @DefaultValue("4") @Min(1) int maxSize,
            @DefaultValue("500") @Min(0) int queueCapacity
    ) {
    }
}
