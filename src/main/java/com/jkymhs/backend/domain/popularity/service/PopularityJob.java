package com.jkymhs.backend.domain.popularity.service;

import com.jkymhs.backend.domain.behavior.service.BehaviorStatsService;
import com.jkymhs.backend.domain.content.batch.BatchAlertNotifier;
import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.content.service.ContentRedisKeys;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.wish.service.WishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// 트랜잭션 금지: 점수 갱신 커밋 전에 버전이 올라간다
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularityJob {
    private static final int VIEW_WEIGHT = 1;
    private static final int WISH_WEIGHT = 3;
    private static final int SAVE_WEIGHT = 5;

    private final BehaviorStatsService behaviorStatsService;
    private final WishService wishService;
    private final ContentService contentService;
    private final BatchJobLogService batchJobLogService;
    private final BatchAlertNotifier alertNotifier;
    private final StringRedisTemplate redisTemplate;
    private final PopularityProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${popularity.cron:0 0 * * * *}", zone = "Asia/Seoul")
    public void scheduledRun() {
        run();
    }

    public int run() {
        BatchJobLog jobLog = batchJobLogService.start(BatchJobName.POPULARITY);
        try {
            LocalDateTime since = LocalDateTime.now(clock).minus(properties.window());
            Map<Long, Long> raw = new HashMap<>();
            add(raw, behaviorStatsService.countViewsSince(since.toLocalDate()), VIEW_WEIGHT);
            add(raw, wishService.countCreatedSince(since), WISH_WEIGHT);
            add(raw, behaviorStatsService.countSavesSince(since), SAVE_WEIGHT);

            Map<Long, Integer> scores = PopularityScores.normalize(raw);
            contentService.updatePopularityScores(scores);
            bumpVersion();

            batchJobLogService.finish(jobLog, BatchJobStatus.SUCCESS, scores.size(), 0, 0, null);
            log.info("Popularity scores updated: {} contents", scores.size());
            return scores.size();
        } catch (RuntimeException e) {
            log.error("Popularity batch failed", e);
            batchJobLogService.finish(jobLog, BatchJobStatus.FAILED, 0, 0, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            alertNotifier.notifyFailure("[POPULARITY] 인기 점수 배치 실패: " + e.getMessage());
            return -1;
        }
    }

    private static void add(Map<Long, Long> raw, Map<Long, Long> counts, int weight) {
        counts.forEach((id, count) -> raw.merge(id, count * weight, Long::sum));
    }

    private void bumpVersion() {
        try {
            redisTemplate.opsForValue().increment(ContentRedisKeys.popularityVersion());
        } catch (DataAccessException e) {
            log.warn("popularity version increment failed: {}", e.getMessage());
        }
    }
}
