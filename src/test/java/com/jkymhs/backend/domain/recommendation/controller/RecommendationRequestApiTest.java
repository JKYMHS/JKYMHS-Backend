package com.jkymhs.backend.domain.recommendation.controller;

import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.service.RecommendationConfig;
import com.jkymhs.backend.domain.recommendation.service.RecommendationService;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationRequestApiTest extends RecommendationTestSupport {

    @Autowired
    private RecommendationService recommendationService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        loadSeoulPlaces();
        user = saveUser("rec@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("요청하면 202와 남은 횟수를 주고, 작업이 끝난 뒤 결과 API는 DONE과 코스 3개를 준다")
    void requestThenDone() throws Exception {
        MvcResult accepted = request(token, body())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.left").value(9))
                .andExpect(jsonPath("$.pollAfterMs").value(2000))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andReturn();
        long requestId = longAt(accepted, "$.requestId");

        MvcResult result = result(token, requestId);
        assertThat((String) read(result, "$.status")).isEqualTo("DONE");
        assertThat((Boolean) read(result, "$.isFallback")).isFalse();
        assertThat((List<?>) read(result, "$.courses")).hasSize(3);
        assertThat((List<String>) read(result, "$.courses[*].courseId")).containsExactly("c1", "c2", "c3");
        assertThat((String) read(result, "$.conditions.regionName")).isEqualTo("서울");
        assertThat((String) read(result, "$.conditions.companion")).isEqualTo("friends");
        assertThat((String) read(result, "$.conditions.startTime")).isEqualTo("10:00");
        assertThat(jdbcTemplate.queryForObject("SELECT last_companion_type FROM users WHERE id = ?", String.class,
                user.getId())).isEqualTo("FRIENDS");
    }

    @Test
    @DisplayName("기간이 4일을 넘거나 가는 날이 지났으면 400 SCHEDULE_LIMIT_EXCEEDED와 상황별 문구다")
    void periodRules() throws Exception {
        request(token, body(TODAY, TODAY.plusDays(4), "friends", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("여행 기간은 최대 3박 4일까지 선택할 수 있습니다."));
        request(token, body(TODAY.minusDays(1), TODAY, "friends", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("지난 날짜는 선택할 수 없습니다."));
        request(token, body(TODAY, TODAY.plusDays(3), "friends", "")).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("필수 값이 없거나 아이와 가는데 나이가 없으면 400 INVALID_INPUT이다")
    void validation() throws Exception {
        request(token, """
                {"startDate": "%s", "endDate": "%s", "origin": {"name": "서울역", "lat": 37.5, "lng": 126.9},
                 "companion": "friends"}
                """.formatted(TODAY, TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("지역을 선택해주세요."));
        request(token, body(TODAY, TODAY, "kids", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("아이 나이를 선택해주세요."));
        request(token, body(TODAY, TODAY, "pets", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        request(token, body(TODAY, TODAY, "kids", ", \"childAgeGroup\": \"ELEMENTARY\""))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("시·군·구가 그 시·도 아래가 아니면 400, 아래면 그 시·군·구만 후보가 된다")
    void districts() throws Exception {
        Long haeundaeId = ContentFixtures.regionId(regionService.getAllRegions(), "부산 해운대구");
        request(token, body(TODAY, TODAY, "friends", ", \"districtIds\": [%d]".formatted(haeundaeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        long requestId = recommend(token, body(TODAY, TODAY, "friends", ", \"districtIds\": [%d]".formatted(jongnoId)));
        MvcResult result = result(token, requestId);
        assertThat((List<Integer>) read(result, "$.conditions.districtIds")).containsExactly(jongnoId.intValue());
        assertThat((List<String>) read(result, "$.conditions.districtNames")).containsExactly("종로구");
        assertThat((String) read(result, "$.status")).isEqualTo("DONE");
    }

    @Test
    @DisplayName("꼭 포함할 장소는 5개까지이고, 없는 장소는 404, 기간·지역이 안 맞으면 막지 않고 경고만 준다")
    void requiredPlaces() throws Exception {
        request(token, body(TODAY, TODAY, "friends", ", \"requiredContentIds\": [1001, 1002, 1003, 1004, 1005, 1006]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("꼭 포함할 장소는 최대 5개까지 고를 수 있습니다."));
        request(token, body(TODAY, TODAY, "friends", ", \"requiredContentIds\": [999999]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));

        load(festival(4001, "지난 축제", 37.5710, 126.9780, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)),
                ContentFixtures.attraction(4002, "해운대", "26", "350"));
        MvcResult accepted = request(token, body(TODAY, TODAY, "friends",
                ", \"requiredContentIds\": [4001, 4002, 4001]"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.warnings", hasSize(2)))
                .andExpect(jsonPath("$.warnings[0].contentId").value(4001))
                .andExpect(jsonPath("$.warnings[0].code").value("OUT_OF_PERIOD"))
                .andExpect(jsonPath("$.warnings[0].availableFrom").value("2026-10-01"))
                .andExpect(jsonPath("$.warnings[0].availableTo").value("2026-10-03"))
                .andExpect(jsonPath("$.warnings[1].contentId").value(4002))
                .andExpect(jsonPath("$.warnings[1].code").value("OUT_OF_REGION"))
                .andReturn();

        // 꼭 포함할 장소는 경고가 있어도 모든 코스에 들어가고 isRequired=true다
        MvcResult result = result(token, longAt(accepted, "$.requestId"));
        for (int c = 0; c < 3; c++) {
            List<Integer> required = read(result,
                    "$.courses[" + c + "].days[*].items[?(@.type == 'place' && @.isRequired == true)].contentId");
            assertThat(required).containsExactlyInAnyOrder(4001, 4002);
        }
        assertThat(jdbcTemplate.queryForList(
                "SELECT content_id FROM recommendation_required_places ORDER BY sort_order", Long.class))
                .containsExactly(4001L, 4002L);
    }

    @Test
    @DisplayName("오늘 10회를 쓰면 429이고, 실패한 요청은 세지 않는다")
    void dailyLimit() throws Exception {
        for (int i = 0; i < 9; i++) {
            insertRequest(user.getId(), RecommendationStatus.DONE, LocalDateTime.now(clock));
        }
        insertRequest(user.getId(), RecommendationStatus.FAILED, LocalDateTime.now(clock));
        request(token, body()).andExpect(status().isAccepted()).andExpect(jsonPath("$.left").value(0));

        request(token, body(TODAY, TODAY, "couple", ""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("오늘 추천 횟수(10회)를 모두 사용했습니다."));
    }

    @Test
    @DisplayName("같은 조건의 PENDING 요청이 10초 안에 있으면 새로 만들지 않고 그 요청을 돌려준다")
    void dedupe() throws Exception {
        // 첫 요청을 보내 조건 키를 만든 뒤 PENDING으로 되돌려 "아직 작업 중"인 상태를 만든다
        long first = recommend(token, body());
        jdbcTemplate.update("UPDATE recommendation_requests SET status = 'PENDING' WHERE id = ?", first);

        clock.advance(Duration.ofSeconds(5));
        request(token, body()).andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value(first));
        assertThat(requestRepository.count()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(6));
        MvcResult later = request(token, body()).andExpect(status().isAccepted()).andReturn();
        assertThat(longAt(later, "$.requestId")).isNotEqualTo(first);
        assertThat(requestRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("targetTripId가 남의 여행이면 403, 없는 여행이면 404다")
    void targetTrip() throws Exception {
        String otherToken = token(saveUser("other@example.com"));
        long[] otherTrip = createTrip(otherToken, "남의 여행", TODAY, TODAY.plusDays(1));
        long[] myTrip = createTrip(token, "내 여행", TODAY, TODAY.plusDays(1));

        request(token, body(TODAY, TODAY, "friends", ", \"targetTripId\": %d".formatted(otherTrip[0])))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        request(token, body(TODAY, TODAY, "friends", ", \"targetTripId\": 999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
        long requestId = recommend(token, body(TODAY, TODAY, "friends", ", \"targetTripId\": %d".formatted(myTrip[0])));
        assertThat(requestRepository.findById(requestId).orElseThrow().getTargetTripId()).isEqualTo(myTrip[0]);
    }

    @Test
    @DisplayName("작업 실행기가 가득 차면 요청을 FAILED로 두고 503이며, 횟수는 쓰지 않는다")
    void executorRejected() throws Exception {
        Object original = ReflectionTestUtils.getField(recommendationService, "executor");
        TaskExecutor rejecting = task -> {
            throw new TaskRejectedException("가득 참");
        };
        ReflectionTestUtils.setField(recommendationService, "executor", rejecting);
        try {
            request(token, body())
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value("추천 서비스를 잠시 사용할 수 없습니다. 잠시 후 다시 시도해주세요."));
        } finally {
            ReflectionTestUtils.setField(recommendationService, "executor", original);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT failure_reason FROM recommendation_requests", String.class))
                .isEqualTo("AI_UNAVAILABLE");
        assertThat(RecommendationConfig.EXECUTOR).isEqualTo("recommendationExecutor");
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void unauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/recommendations")
                        .contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
