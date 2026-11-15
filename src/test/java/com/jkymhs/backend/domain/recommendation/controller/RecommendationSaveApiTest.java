package com.jkymhs.backend.domain.recommendation.controller;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanSwitchedEvent;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationSaveApiTest extends RecommendationTestSupport {

    private static final String EXPIRED = "추천 결과가 만료되었습니다. 다시 추천받아주세요.";

    private User user;
    private String token;
    private long requestId;

    @BeforeEach
    void setUp() throws Exception {
        loadSeoulPlaces();
        user = saveUser("save@example.com");
        token = token(user);
        requestId = recommend(token, body());
    }

    @Test
    @DisplayName("새 여행으로 저장하면 추천 조건으로 채운 여행과 확정된 일정안이 생기고 일정 담기 신호가 나간다")
    void newTripWithDefaults() throws Exception {
        MvcResult saved = save("""
                {"courseId": "c1", "target": {"newTrip": {}}}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isConfirmed").value(true))
                .andReturn();
        long tripId = longAt(saved, "$.tripId");
        long planId = longAt(saved, "$.planId");

        Map<String, Object> trip = jdbcTemplate.queryForMap(
                "SELECT title, start_date, end_date, companion, confirmed_plan_id, origin_name FROM trips WHERE id = ?",
                tripId);
        assertThat(trip.get("title")).isEqualTo("서울 1박 2일");
        assertThat(trip.get("start_date").toString()).isEqualTo(TODAY.toString());
        assertThat(trip.get("end_date").toString()).isEqualTo(TODAY.plusDays(1).toString());
        assertThat(trip.get("companion")).isEqualTo("FRIENDS");
        assertThat(((Number) trip.get("confirmed_plan_id")).longValue()).isEqualTo(planId);
        assertThat(trip.get("origin_name")).isEqualTo("서울역");
        assertThat(jdbcTemplate.queryForList("SELECT region_id FROM trip_regions WHERE trip_id = ?", Long.class, tripId))
                .containsExactly(seoulId);

        Map<String, Object> plan = jdbcTemplate.queryForMap("SELECT name, source, ai_request_id FROM plans WHERE id = ?",
                planId);
        assertThat(plan.get("name")).isEqualTo("AI 추천 · 11.09");
        assertThat(plan.get("source")).isEqualTo("AI");
        assertThat(((Number) plan.get("ai_request_id")).longValue()).isEqualTo(requestId);

        List<Integer> coursePlaces = read(result(token, requestId),
                "$.courses[0].days[*].items[?(@.type == 'place')].contentId");
        List<Map<String, Object>> items = planItems(planId);
        assertThat(items).extracting(i -> ((Number) i.get("content_id")).intValue()).containsExactlyElementsOf(coursePlaces);
        // 첫날 첫 장소는 출발지에서 오는 이동이 있고, 둘째 날 첫 장소는 이동이 없다
        assertThat(items.getFirst().get("transport_mode")).isNotNull();
        assertThat(items.stream().filter(i -> ((Number) i.get("day_number")).intValue() == 2).findFirst().orElseThrow()
                .get("transport_mode")).isNull();
        assertThat(items.getFirst().get("ai_reason")).isEqualTo("궁 · 인기 장소");
        assertThat(eventsOf(BehaviorSignalEvent.class)).filteredOn(e -> e.type() == BehaviorSignalType.SCHEDULE_SAVE)
                .hasSize(coursePlaces.size());
    }

    @Test
    @DisplayName("기존 여행에 확정안으로 저장하면 확정안이 바뀌고(전환 이벤트), 확정하지 않으면 초안으로만 들어간다")
    void existingTrip() throws Exception {
        long[] trip = createTrip(token, "내 여행", TODAY, TODAY.plusDays(2));

        MvcResult draft = save("""
                {"courseId": "c2", "target": {"tripId": %d}, "planName": "둘째 안"}
                """.formatted(trip[0])).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isConfirmed").value(false)).andReturn();
        assertThat(confirmedPlanOf(trip[0])).isEqualTo(trip[1]);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM plans WHERE id = ?", String.class,
                longAt(draft, "$.planId"))).isEqualTo("둘째 안");

        MvcResult confirmed = save("""
                {"courseId": "c1", "target": {"tripId": %d}, "makeConfirmed": true}
                """.formatted(trip[0])).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isConfirmed").value(true)).andReturn();
        long planId = longAt(confirmed, "$.planId");
        assertThat(confirmedPlanOf(trip[0])).isEqualTo(planId);
        assertThat(eventsOf(ConfirmedPlanSwitchedEvent.class)).singleElement()
                .satisfies(e -> assertThat(e.newPlanId()).isEqualTo(planId));
    }

    @Test
    @DisplayName("여행이 코스보다 짧으면 422, 남의 여행이면 403이다")
    void tripErrors() throws Exception {
        long[] shortTrip = createTrip(token, "당일", TODAY, TODAY);
        save("""
                {"courseId": "c1", "target": {"tripId": %d}}
                """.formatted(shortTrip[0]))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLAN_EXCEEDS_TRIP_PERIOD"));

        long[] otherTrip = createTrip(token(saveUser("other@example.com")), "남의 여행", TODAY, TODAY.plusDays(1));
        save("""
                {"courseId": "c1", "target": {"tripId": %d}}
                """.formatted(otherTrip[0]))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plans WHERE source = 'AI'", Long.class)).isZero();
    }

    @Test
    @DisplayName("고른 식사는 그 칸의 시각으로 음식점 항목이 되고, 그 뒤 장소의 이동은 음식점에서 다시 계산한다")
    void mealSelection() throws Exception {
        MvcResult result = result(token, requestId);
        String lunchStart = ((List<String>) read(result,
                "$.courses[0].days[0].items[?(@.type == 'meal')].startTime")).getFirst();

        MvcResult saved = save("""
                {"courseId": "c1", "target": {"newTrip": {"title": "점심 포함"}},
                 "mealSelections": [{"dayNumber": 1, "mealType": "LUNCH", "contentId": 3001}]}
                """).andExpect(status().isCreated()).andReturn();

        List<Map<String, Object>> day1 = planItems(longAt(saved, "$.planId")).stream()
                .filter(i -> ((Number) i.get("day_number")).intValue() == 1).toList();
        assertThat(day1).hasSize(4);
        Map<String, Object> lunch = day1.get(1);
        assertThat(((Number) lunch.get("content_id")).longValue()).isEqualTo(3001L);
        assertThat(lunch.get("start_time").toString()).startsWith(lunchStart);
        assertThat(((Number) lunch.get("stay_minutes")).intValue()).isEqualTo(60);
        assertThat(lunch.get("transport_mode")).isNotNull();
        assertThat(day1.get(2).get("transport_mode")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM trips", String.class)).isEqualTo("점심 포함");
    }

    @Test
    @DisplayName("식사에 음식점이 아닌 곳을 넣거나 코스에 없는 식사 칸을 고르면 400이다")
    void mealErrors() throws Exception {
        save("""
                {"courseId": "c1", "target": {"newTrip": {}},
                 "mealSelections": [{"dayNumber": 1, "mealType": "LUNCH", "contentId": 1005}]}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("식사 시간에는 음식점만 넣을 수 있습니다."));
        save("""
                {"courseId": "c1", "target": {"newTrip": {}},
                 "mealSelections": [{"dayNumber": 1, "mealType": "DINNER", "contentId": 3001}]}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("선택한 식사 시간이 코스에 없습니다."));
    }

    @Test
    @DisplayName("저장 대상은 기존 여행과 새 여행 중 정확히 하나여야 한다")
    void targetValidation() throws Exception {
        save("""
                {"courseId": "c1", "target": {"tripId": 1, "newTrip": {}}}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("저장할 여행을 하나만 선택해주세요."));
        save("""
                {"courseId": "c1", "target": {}}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        save("""
                {"courseId": "c1"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("저장할 여행을 선택해주세요."));
    }

    @Test
    @DisplayName("없는 코스나 만료된 결과는 404다")
    void notFound() throws Exception {
        save("""
                {"courseId": "c9", "target": {"newTrip": {}}}
                """).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(EXPIRED));
        clock.advance(Duration.ofHours(25));
        save("""
                {"courseId": "c1", "target": {"newTrip": {}}}
                """).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(EXPIRED));
    }

    @Test
    @DisplayName("다시 추천 결과를 저장하면 일정안 출처가 AI_RETRY다")
    void retrySource() throws Exception {
        MvcResult retried = mockMvc.perform(json(post("/api/recommendations/{id}/retry", requestId), token, "{}"))
                .andExpect(status().isAccepted()).andReturn();
        long retryId = longAt(retried, "$.requestId");

        MvcResult saved = mockMvc.perform(json(post("/api/recommendations/{id}/save", retryId), token, """
                {"courseId": "c1", "target": {"newTrip": {}}}
                """)).andExpect(status().isCreated()).andReturn();
        assertThat(jdbcTemplate.queryForObject("SELECT source FROM plans WHERE id = ?", String.class,
                longAt(saved, "$.planId"))).isEqualTo("AI_RETRY");
    }

    @Test
    @DisplayName("코스에서 뺀 장소는 저장하지 않고 AI_REMOVE 신호를 보낸다 (코스에 없던 id는 무시)")
    void removedPlaces() throws Exception {
        MvcResult saved = save("""
                {"courseId": "c1", "target": {"newTrip": {}}, "removedContentIds": [1002, 2004]}
                """).andExpect(status().isCreated()).andReturn();

        assertThat(planItems(longAt(saved, "$.planId")))
                .extracting(i -> ((Number) i.get("content_id")).longValue()).doesNotContain(1002L).hasSize(5);
        assertThat(eventsOf(BehaviorSignalEvent.class)).filteredOn(e -> e.type() == BehaviorSignalType.AI_REMOVE)
                .singleElement().satisfies(e -> {
                    assertThat(e.contentId()).isEqualTo(1002L);
                    assertThat(e.companion()).isEqualTo("friends");
                    assertThat(e.tripId()).isEqualTo(longAt(saved, "$.tripId"));
                });
    }

    private ResultActions save(String body) throws Exception {
        return mockMvc.perform(json(post("/api/recommendations/{id}/save", requestId), token, body));
    }

    private List<Map<String, Object>> planItems(long planId) {
        return jdbcTemplate.queryForList("""
                SELECT content_id, day_number, sort_order, start_time, stay_minutes, transport_mode, ai_reason
                FROM plan_items WHERE plan_id = ? ORDER BY day_number, sort_order""", planId);
    }

    private long confirmedPlanOf(long tripId) {
        return jdbcTemplate.queryForObject("SELECT confirmed_plan_id FROM trips WHERE id = ?", Long.class, tripId);
    }
}
