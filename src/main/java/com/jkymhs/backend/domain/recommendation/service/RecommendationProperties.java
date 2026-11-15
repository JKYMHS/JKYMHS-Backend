package com.jkymhs.backend.domain.recommendation.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code recommendation.*}.
 *
 * @param dailyLimit         하루(KST) 추천 횟수. 다시 추천도 1회로 센다
 * @param resultTtl          결과 보관 기간 (Redis rec:result, 요청 행의 expires_at)
 * @param candidateCacheTtl  지역·기간별 후보 풀 캐시 기간
 * @param candidatePoolSize  후보 풀 크기 (인기 점수 순)
 * @param candidateLimit     회원 점수를 매긴 뒤 AI에 넘기는 후보 수
 * @param deadline           작업 전체 제한 시간. 넘으면 AI 재시도·외부 경로 호출을 건너뛴다
 * @param dedupeWindow       같은 조건으로 이 시간 안에 다시 오면 새 요청을 만들지 않는다
 * @param pendingTimeout     이 시간이 지나도 PENDING이면 정리 배치가 FAILED로 바꾼다 (서버가 작업 중에 죽은 경우)
 * @param originRetention    출발지 좌표·주소를 지우기 전까지 보관하는 시간
 */
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
