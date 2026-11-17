package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.entity.RecommendationStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 추천 결과·진행 상황의 Redis 읽기/쓰기.
 *
 * 결과는 "꼭 남아야 하는 값"이라 Redis 오류를 숨기지 않는다: 쓰기 실패 → 작업이 FAILED(INTERNAL), 읽기 실패 → 503 (G12).
 * 진행 상황은 화면 표시용이라 실패해도 무시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationResultStore {

    static final Duration PROGRESS_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RecommendationProperties properties;

    /**
     * @throws DataAccessException Redis 쓰기 실패
     */
    public void saveResult(Long requestId, RecommendationResult result) {
        redisTemplate.opsForValue().set(RecommendationRedisKeys.result(requestId), jsonMapper.writeValueAsString(result),
                properties.resultTtl());
    }

    /**
     * @return 없거나(만료) 형식이 깨졌으면 빈 값
     * @throws DataAccessException Redis 읽기 실패 (전역 처리기가 503으로 바꾼다)
     */
    public Optional<RecommendationResult> findResult(Long requestId) {
        String json = redisTemplate.opsForValue().get(RecommendationRedisKeys.result(requestId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, RecommendationResult.class));
        } catch (JacksonException e) {
            log.warn("추천 결과 형식 오류 (없는 것으로 봄): requestId={}, {}", requestId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 이전 결과를 참고만 할 때 (다시 추천의 감점). Redis 오류도 빈 값으로 본다 */
    public Optional<RecommendationResult> findResultQuietly(Long requestId) {
        try {
            return findResult(requestId);
        } catch (DataAccessException e) {
            log.warn("이전 추천 결과 조회 실패 (무시): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void saveProgress(Long requestId, RecommendationStep step, int candidateCount, int selectedCount) {
        String key = RecommendationRedisKeys.progress(requestId);
        Map<String, String> hash = new HashMap<>();
        hash.put("step", step.name());
        hash.put("stepIndex", String.valueOf(step.index()));
        hash.put("stepTotal", String.valueOf(RecommendationStep.TOTAL));
        hash.put("candidateCount", String.valueOf(candidateCount));
        hash.put("selectedCount", String.valueOf(selectedCount));
        try {
            redisTemplate.opsForHash().putAll(key, hash);
            redisTemplate.expire(key, PROGRESS_TTL);
        } catch (DataAccessException e) {
            log.warn("추천 진행 상황 저장 실패 (무시): {}", e.getMessage());
        }
    }

    /** 진행 상황이 없으면(아직 시작 전, Redis 오류) 빈 값 */
    public Optional<Progress> findProgress(Long requestId) {
        try {
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(RecommendationRedisKeys.progress(requestId));
            if (hash == null || hash.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Progress(RecommendationStep.valueOf((String) hash.get("step")),
                    intOf(hash.get("candidateCount")), intOf(hash.get("selectedCount"))));
        } catch (DataAccessException | IllegalArgumentException e) {
            log.warn("추천 진행 상황 조회 실패 (대기 중으로 보여줌): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void deleteAll(Collection<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (Long id : requestIds) {
            keys.add(RecommendationRedisKeys.result(id));
            keys.add(RecommendationRedisKeys.progress(id));
        }
        try {
            redisTemplate.delete(keys);
        } catch (DataAccessException e) {
            // 남은 키는 TTL(최대 24시간)이 지나면 사라진다
            log.warn("추천 Redis 키 삭제 실패 (TTL로 사라짐): {}", e.getMessage());
        }
    }

    private static int intOf(Object value) {
        try {
            return value == null ? 0 : Integer.parseInt((String) value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public record Progress(RecommendationStep step, int candidateCount, int selectedCount) {
    }
}
