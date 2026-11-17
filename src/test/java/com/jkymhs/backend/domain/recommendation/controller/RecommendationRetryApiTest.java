package com.jkymhs.backend.domain.recommendation.controller;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.service.RecommendationRedisKeys;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationRetryApiTest extends RecommendationTestSupport {

    private static final String EXPIRED = "이전 추천 결과가 만료되었습니다. 조건을 다시 입력해주세요.";

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        loadSeoulPlaces();
        user = saveUser("retry@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("다시 추천은 같은 조건의 새 요청(AI_RETRY)을 만들고 직전 요청과 연결하며 1회를 쓴다")
    void retryCreatesLinkedRequest() throws Exception {
        long first = recommend(token, body(TODAY, TODAY, "friends", ", \"districtIds\": [%d]".formatted(jongnoId)));

        MvcResult accepted = retry(first, "{}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.previousRequestId").value(first))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.left").value(8))
                .andReturn();
        long second = longAt(accepted, "$.requestId");

        assertThat(second).isNotEqualTo(first);
        RecommendationRequest saved = requestRepository.findById(second).orElseThrow();
        assertThat(saved.getSource().name()).isEqualTo("AI_RETRY");
        assertThat(saved.getPreviousRequestId()).isEqualTo(first);
        MvcResult result = result(token, second);
        assertThat((Integer) read(result, "$.previousRequestId")).isEqualTo((int) first);
        assertThat((List<Integer>) read(result, "$.conditions.districtIds")).containsExactly(jongnoId.intValue());
    }

    @Test
    @DisplayName("빼달라고 한 곳과 직전 코스의 장소는 점수가 깎여서 새 첫 코스의 앞자리에 오지 않는다")
    void penalized() throws Exception {
        long first = recommend(token, body(TODAY, TODAY, "friends", ""));
        List<Integer> firstCourse = read(result(token, first),
                "$.courses[0].days[0].items[?(@.type == 'place')].contentId");
        assertThat(firstCourse).containsExactlyInAnyOrder(1001, 1002, 1003);

        long second = longAt(retry(first, "{\"excludeContentIds\": [1004]}").andReturn(), "$.requestId");

        List<Integer> secondCourse = read(result(token, second),
                "$.courses[0].days[0].items[?(@.type == 'place')].contentId");
        assertThat(secondCourse).doesNotContain(1004).doesNotContainAnyElementsOf(firstCourse);
        assertThat(jdbcTemplate.queryForObject("SELECT excluded_content_ids FROM recommendation_requests WHERE id = ?",
                String.class, second)).isEqualTo("1004");
    }

    @Test
    @DisplayName("결과가 없거나(실패·만료·Redis에서 사라짐) 남의 요청이면 404, 횟수를 다 쓰면 429다")
    void errors() throws Exception {
        RecommendationRequest failed = insertRequest(user.getId(), RecommendationStatus.FAILED, LocalDateTime.now(clock));
        retry(failed.getId(), "{}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(EXPIRED));

        long first = recommend(token, body(TODAY, TODAY, "friends", ""));
        String otherToken = token(saveUser("other@example.com"));
        mockMvc.perform(json(post("/api/recommendations/{id}/retry", first), otherToken, "{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(EXPIRED));

        for (int i = 0; i < 9; i++) {
            insertRequest(user.getId(), RecommendationStatus.DONE, LocalDateTime.now(clock));
        }
        retry(first, "{}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT_EXCEEDED"));

        redisTemplate.delete(RecommendationRedisKeys.result(first));
        retry(first, "{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(EXPIRED));
    }

    @Test
    @DisplayName("24시간이 지난 결과는 다시 추천할 수 없다")
    void expired() throws Exception {
        long first = recommend(token, body(TODAY, TODAY, "friends", ""));
        clock.advance(Duration.ofHours(24).plusMinutes(1));
        retry(first, "{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(EXPIRED));
    }

    private ResultActions retry(long requestId, String body) throws Exception {
        return mockMvc.perform(json(post("/api/recommendations/{id}/retry", requestId), token, body));
    }
}
