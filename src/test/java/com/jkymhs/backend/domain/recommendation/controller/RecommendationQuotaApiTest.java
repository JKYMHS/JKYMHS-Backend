package com.jkymhs.backend.domain.recommendation.controller;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationQuotaApiTest extends RecommendationTestSupport {

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = saveUser("quota@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("처음에는 10회가 남아 있고, 기본 동행은 없고 시작 시각은 취향 기본값(오전 10:00)이다")
    void initial() throws Exception {
        quota()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.left").value(10))
                .andExpect(jsonPath("$.resetAt").value("2026-11-10T00:00:00+09:00"))
                .andExpect(jsonPath("$.defaults.companion").value(nullValue()))
                .andExpect(jsonPath("$.defaults.startTime").value("10:00"));
    }

    @Test
    @DisplayName("오늘(KST) FAILED가 아닌 요청만 세고, 자정이 지나면 0부터 다시 센다")
    void countsTodayExceptFailed() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        insertRequest(user.getId(), RecommendationStatus.DONE, now);
        insertRequest(user.getId(), RecommendationStatus.FALLBACK, now);
        insertRequest(user.getId(), RecommendationStatus.PENDING, now);
        insertRequest(user.getId(), RecommendationStatus.FAILED, now);
        insertRequest(user.getId(), RecommendationStatus.DONE, now.minusDays(1));

        quota().andExpect(jsonPath("$.used").value(3)).andExpect(jsonPath("$.left").value(7));

        // 23:59 KST까지는 같은 날, 00:00이 되면 새 날
        clock.setNow(TODAY.atTime(23, 59));
        quota().andExpect(jsonPath("$.used").value(3));
        clock.setNow(TODAY.plusDays(1).atStartOfDay());
        quota().andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.resetAt").value("2026-11-11T00:00:00+09:00"));
    }

    @Test
    @DisplayName("마지막 추천 요청의 동행과 취향의 시작 시간대가 기본값으로 나온다")
    void defaults() throws Exception {
        loadSeoulPlaces();
        recommend(token, body(TODAY, TODAY, "parents", ""));
        mockMvc.perform(json(put("/api/members/me/preferences"), token, """
                {"styleIds": ["culture"], "pace": "packed", "timeOfDay": "afternoon", "accessible": false, "pet": false}
                """)).andExpect(status().isOk());

        quota()
                .andExpect(jsonPath("$.used").value(1))
                .andExpect(jsonPath("$.defaults.companion").value("parents"))
                .andExpect(jsonPath("$.defaults.startTime").value("13:00"));
    }

    private ResultActions quota() throws Exception {
        return mockMvc.perform(auth(get("/api/recommendations/quota"), token));
    }
}
