package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오늘 = 2026-11-09. 끝난 여행 = 11-01 ~ 11-02, 장소 3개.
 */
class VisitCheckApiTest extends TripApiTestSupport {

    private String token;
    private long tripId;
    private List<Long> itemIds;

    @BeforeEach
    void setUp() throws Exception {
        token = token(saveUser("visit@example.com", ConsentType.BEHAVIOR_DATA));
        long[] ids = createTrip(token, "지난 여행", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        tripId = ids[0];
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910),
                place(103L, "덕수궁", 37.5658, 126.9751));
        savePlan(token, tripId, ids[1], 0, items(item(null, 101L, 1, 1), item(null, 102L, 1, 2), item(null, 103L, 2, 1)))
                .andExpect(status().isOk());
        itemIds = itemIds(ids[1]);
        events.clear();
    }

    @Test
    @DisplayName("끝났고 확정안에 장소가 있으면 배너에 뜨고 PENDING이 된다. 행동 데이터 동의가 없거나 진행 중인 여행은 뜨지 않는다")
    void pending() throws Exception {
        createTrip(token, "진행 중", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10));

        mockMvc.perform(auth(get("/api/trips/visit-check/pending"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.id").value(tripId))
                .andExpect(jsonPath("$.trip.title").value("지난 여행"))
                .andExpect(jsonPath("$.trip.endDate").value("2026-11-02"));
        assertThat(visitStatus()).isEqualTo("PENDING");

        long noConsentUser = saveUser("noconsent@example.com").getId();
        String noConsent = token(userRepository.findById(noConsentUser).orElseThrow());
        long[] other = createTrip(noConsent, "동의 없음", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        savePlan(noConsent, other[0], other[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/trips/visit-check/pending"), noConsent))
                .andExpect(jsonPath("$.trip").value(nullValue()));
    }

    @Test
    @DisplayName("방문 확인 화면은 확정안 장소를 날짜별로 보여주고, 끝나지 않은 여행은 409 VISIT_CHECK_NOT_AVAILABLE")
    void visitCheckScreen() throws Exception {
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/{id}/visit-check", tripId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checked").value(0))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.days[0].items[0].name").value("경복궁"))
                .andExpect(jsonPath("$.days[0].items[0].thumbnailUrl").value("https://img.test/101.jpg"))
                .andExpect(jsonPath("$.days[0].items[0].visited").value(nullValue()))
                .andExpect(jsonPath("$.days[1].items.length()").value(1));

        long[] future = createTrip(token, "다가오는 여행", TODAY.plusDays(1), TODAY.plusDays(1));
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/{id}/visit-check", future[0]), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_CHECK_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("아직 끝나지 않았거나 이미 확인한 여행입니다."));
    }

    @Test
    @DisplayName("일부만 저장하면 PENDING, 모두 답하면 DONE이고 배너에서 빠진다. 처음 답한 장소만 만족도별 행동 신호를 낸다")
    void partialSaveAndDone() throws Exception {
        save("""
                {"itemId": %d, "visited": true, "rating": 5},
                {"itemId": %d, "visited": false, "rating": 0}
                """.formatted(itemIds.get(0), itemIds.get(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checked").value(2))
                .andExpect(jsonPath("$.total").value(3));

        save("""
                {"itemId": %d, "visited": true, "rating": 2},
                {"itemId": %d, "visited": true, "rating": 3}
                """.formatted(itemIds.get(0), itemIds.get(2)))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.checked").value(3));

        assertThat(jdbcTemplate.queryForList("SELECT visited, rating FROM visit_reviews ORDER BY plan_item_id"))
                .extracting(row -> row.get("rating"))
                .containsExactly(2, null, 3);
        // 경복궁 수정(5 → 2)은 다시 신호를 내지 않는다
        assertThat(eventsOf(BehaviorSignalEvent.class)).extracting(BehaviorSignalEvent::contentId, BehaviorSignalEvent::type)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(101L, BehaviorSignalType.VISIT_SATISFIED),
                        org.assertj.core.groups.Tuple.tuple(103L, BehaviorSignalType.VISIT_NEUTRAL));
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/visit-check/pending"), token))
                .andExpect(jsonPath("$.trip").value(nullValue()));
    }

    @Test
    @DisplayName("갔는데 만족도가 없거나 0·6이면 400, 안 갔는데 만족도 3이면 400, 확정안에 없는 항목은 400")
    void ratingValidation() throws Exception {
        for (String answer : List.of("\"visited\": true", "\"visited\": true, \"rating\": 0",
                "\"visited\": true, \"rating\": 6", "\"visited\": false, \"rating\": 3")) {
            save("{\"itemId\": %d, %s}".formatted(itemIds.get(0), answer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("만족도는 1~5 사이로 입력해주세요."));
        }
        save("{\"itemId\": 999999, \"visited\": false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM visit_reviews", Long.class)).isZero();
    }

    @Test
    @DisplayName("\"나중에\" 첫 번째는 3일 뒤 다시 묻고, 두 번째는 DISMISSED로 더 묻지 않으며 화면도 409")
    void dismissTwice() throws Exception {
        mockMvc.perform(auth(post("/api/trips/{id}/visit-check/dismiss", tripId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.nextShowAt").value("2026-11-12"));
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/visit-check/pending"), token))
                .andExpect(jsonPath("$.trip").value(nullValue()));

        clock.setNow(LocalDate.of(2026, 11, 12).atTime(9, 0));
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/visit-check/pending"), token))
                .andExpect(jsonPath("$.trip.id").value(tripId));

        mockMvc.perform(auth(post("/api/trips/{id}/visit-check/dismiss", tripId), token))
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.nextShowAt").value(nullValue()));
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/{id}/visit-check", tripId), token))
                .andExpect(status().isConflict());
        mockMvc.perform(auth(post("/api/trips/{id}/visit-check/dismiss", tripId), token))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForMap("SELECT visit_check_status, visit_check_dismiss_count FROM trips WHERE id = ?",
                tripId)).isEqualTo(Map.of("visit_check_status", "DISMISSED", "visit_check_dismiss_count", 2));
    }

    private ResultActions save(String items) throws Exception {
        return mockMvc.perform(json(put("/api/trips/{id}/visit-check", tripId), token, """
                {"items": [%s]}
                """.formatted(items)));
    }

    private String visitStatus() {
        return jdbcTemplate.queryForObject("SELECT visit_check_status FROM trips WHERE id = ?", String.class, tripId);
    }
}
