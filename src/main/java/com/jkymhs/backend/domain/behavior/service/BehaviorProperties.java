package com.jkymhs.backend.domain.behavior.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code behavior.*} (행동 데이터·취향 프로필).
 *
 * @param halfLife            이 기간이 지난 행동은 가중치가 절반이 된다 (30일)
 * @param profileWindow       이 기간보다 오래된 행동은 프로필 계산에서 뺀다 (180일)
 * @param companionMinSignals 동행별 프로필을 쓰려면 필요한 최소 행동 수. 모자라면 공통 프로필을 준다 (10)
 * @param executor            행동 기록 전용 스레드 풀 크기
 */
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
