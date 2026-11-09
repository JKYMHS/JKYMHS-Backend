package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 매시 30분(KST)에 추천 요청을 정리한다.
 * <ul>
 *   <li>24시간 지난 요청의 출발지 좌표·주소를 지운다. 현재 위치일 수 있어서 결과를 볼 수 있는 동안만 둔다 (§5 Q11).</li>
 *   <li>10분 넘게 PENDING인 요청을 FAILED로 바꾼다. 작업 중에 서버가 꺼지면 영원히 PENDING으로 남아 FE가 계속 기다리기 때문이다.
 *       FAILED는 횟수에서 빠지므로 자동으로 돌려준 셈이 된다.</li>
 * </ul>
 */
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
