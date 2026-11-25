package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationCleanupJob {
    private final RecommendationRequestRepository requestRepository;
    private final RecommendationProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${recommendation.cleanup-cron:0 30 * * * *}", zone = "Asia/Seoul")
    public void scheduledRun() {
        run();
    }

    public Result run() {
        LocalDateTime now = LocalDateTime.now(clock);
        int failed = requestRepository.failStalePending(now.minus(properties.pendingTimeout()));
        int cleared = requestRepository.clearOriginsBefore(now.minus(properties.originRetention()));
        if (failed > 0 || cleared > 0) {
            log.info("추천 요청 정리: 멈춘 요청 {}건 실패 처리, 출발지 {}건 삭제", failed, cleared);
        }
        return new Result(failed, cleared);
    }

    public record Result(int failedPending, int clearedOrigins) {
    }
}
