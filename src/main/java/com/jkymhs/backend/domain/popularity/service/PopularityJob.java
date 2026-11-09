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

/**
 * 매시 정각 인기 점수 배치.
 *
 * <ol>
 *   <li>최근 30일 원점수 = 상세 조회 × 1 + 새 찜 × 3 + 일정 담기 × 5 (콘텐츠별)</li>
 *   <li>{@link PopularityScores#normalize}로 0~1000점</li>
 *   <li>{@link ContentService#updatePopularityScores}가 한 트랜잭션으로 전부 바꾼다. 실패하면 롤백돼서 이전 점수가 그대로 남는다.</li>
 *   <li>커밋된 뒤 Redis {@code popularity:version}을 1 올린다. 인기순 목록의 커서에 이 버전이 들어 있어서,
 *       점수가 바뀐 뒤 이전 커서로 다음 페이지를 읽으면 409(목록이 갱신되었습니다)가 난다 (순서가 바뀌어 중복·누락이 생기므로).</li>
 *   <li>batch_job_logs에 POPULARITY 실행 기록을 남긴다. 실패하면 FAILED 기록 + 알림.</li>
 * </ol>
 *
 * 이 메서드에는 트랜잭션을 걸지 않는다. 걸면 updatePopularityScores가 그 트랜잭션에 참여해서
 * 버전을 올리는 시점에 아직 커밋 전이 된다 (다른 요청이 새 버전 + 옛 점수를 보게 된다).
 */
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

    /**
     * @return 0점이 아닌 콘텐츠 수. 실패하면 -1
     */
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

    // 점수는 이미 커밋됐다. Redis가 안 되면 버전만 못 올리고 넘어간다 (목록은 계속 열리고, 그 사이 커서는 버전 검사 없이 이어진다)
    private void bumpVersion() {
        try {
            redisTemplate.opsForValue().increment(ContentRedisKeys.popularityVersion());
        } catch (DataAccessException e) {
            log.warn("popularity version increment failed: {}", e.getMessage());
        }
    }
}
