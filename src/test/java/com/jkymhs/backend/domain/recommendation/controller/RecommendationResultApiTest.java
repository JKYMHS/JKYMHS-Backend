package com.jkymhs.backend.domain.recommendation.controller;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.service.RecommendationRedisKeys;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationResultApiTest extends RecommendationTestSupport {

    private static final String NOT_FOUND = "추천 결과가 만료되었거나 존재하지 않습니다.";

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = saveUser("result@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("작업 중이면 단계·진행 수를 주고, 진행 정보가 아직 없으면 QUEUED(0/5)다")
    void pendingProgress() throws Exception {
        RecommendationRequest pending = insertRequest(user.getId(), RecommendationStatus.PENDING, LocalDateTime.now(clock));
        fetch(pending.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.step").value("QUEUED"))
                .andExpect(jsonPath("$.stepIndex").value(0))
                .andExpect(jsonPath("$.stepTotal").value(5))
                .andExpect(jsonPath("$.pollAfterMs").value(2000))
                .andExpect(jsonPath("$.progress.candidateCount").value(0));

        redisTemplate.opsForHash().putAll(RecommendationRedisKeys.progress(pending.getId()), Map.of(
                "step", "ROUTES", "stepIndex", "4", "stepTotal", "5", "candidateCount", "42", "selectedCount", "12"));
        fetch(pending.getId())
                .andExpect(jsonPath("$.step").value("ROUTES"))
                .andExpect(jsonPath("$.stepIndex").value(4))
                .andExpect(jsonPath("$.progress.candidateCount").value(42))
                .andExpect(jsonPath("$.progress.selectedCount").value(12))
                .andExpect(jsonPath("$.courses").doesNotExist());
    }

    @Test
    @DisplayName("실패한 요청은 FAILED·refunded·reason만 준다")
    void failedShape() throws Exception {
        RecommendationRequest failed = insertRequest(user.getId(), RecommendationStatus.FAILED, LocalDateTime.now(clock));
        fetch(failed.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.refunded").value(true))
                .andExpect(jsonPath("$.reason").value("INTERNAL"))
                .andExpect(jsonPath("$.step").doesNotExist());
    }

    @Test
    @DisplayName("결과에는 조건(지역 이름·출발지 등)과 만료 시각이 같이 나온다")
    void doneShape() throws Exception {
        loadSeoulPlaces();
        long requestId = recommend(token, body(TODAY, TODAY, "couple", ", \"indoor\": true"));

        fetch(requestId)
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.isFallback").value(false))
                .andExpect(jsonPath("$.previousRequestId").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").value("2026-11-10T10:00:00"))
                .andExpect(jsonPath("$.conditions.regionId").value(seoulId))
                .andExpect(jsonPath("$.conditions.indoor").value(true))
                .andExpect(jsonPath("$.conditions.origin.name").value("서울역"))
                .andExpect(jsonPath("$.conditions.origin.lat").value(ORIGIN_LAT))
                .andExpect(jsonPath("$.courses[0].days[0].items[1].type").value("place"))
                .andExpect(jsonPath("$.courses[0].days[0].items[1].startTime").exists())
                .andExpect(jsonPath("$.courses[0].days[0].items[0].polyline").exists());
    }

    @Test
    @DisplayName("실내를 원하면 박물관이 앞에 오고, 실내가 절반이 안 되면 indoorShortage=true다")
    void indoorPreference() throws Exception {
        loadSeoulPlaces();
        long indoor = recommend(token, body(TODAY, TODAY, "couple", ", \"indoor\": true"));
        fetch(indoor)
                .andExpect(jsonPath("$.courses[0].days[0].items[?(@.type == 'place')].io").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("INDOOR"))))
                .andExpect(jsonPath("$.indoorShortage").value(true));
    }

    @Test
    @DisplayName("다른 회원의 요청, 24시간이 지난 요청, Redis 결과가 사라진 요청은 404다")
    void notFound() throws Exception {
        loadSeoulPlaces();
        long requestId = recommend(token, body(TODAY, TODAY, "friends", ""));

        String otherToken = token(saveUser("other@example.com"));
        mockMvc.perform(auth(get("/api/recommendations/{id}", requestId), otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(NOT_FOUND));

        redisTemplate.delete(RecommendationRedisKeys.result(requestId));
        fetch(requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(NOT_FOUND));

        RecommendationRequest pending = insertRequest(user.getId(), RecommendationStatus.PENDING, LocalDateTime.now(clock));
        clock.advance(Duration.ofHours(24));
        fetch(pending.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions fetch(long requestId) throws Exception {
        return mockMvc.perform(auth(get("/api/recommendations/{id}", requestId), token));
    }
}
