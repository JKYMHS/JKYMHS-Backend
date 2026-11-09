package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.RouteRedisKeys;
import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanChangedEvent;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanSaveApiTest extends TripApiTestSupport {

    private String token;
    private long tripId;
    private long planId;

    @BeforeEach
    void setUp() throws Exception {
        token = token(saveUser("save@example.com"));
        long[] ids = createTrip(token, "저장 여행", TODAY.plusDays(1), TODAY.plusDays(2));
        tripId = ids[0];
        planId = ids[1];
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910),
                place(103L, "덕수궁", 37.5658, 126.9751), place(104L, "국립고궁박물관", 37.5800, 126.9775));
    }

    @Test
    @DisplayName("itemId가 있는 항목은 id를 유지하고, 빠진 항목은 지우고, 날짜별 순서를 1..n으로 다시 매기고 버전을 올린다")
    void diffKeepsIds() throws Exception {
        savePlan(token, tripId, planId, 0, items(item(null, 101L, 1, 1), item(null, 102L, 1, 2), item(null, 103L, 2, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        List<Long> ids = itemIds(planId);

        savePlan(token, tripId, planId, 1, items(item(ids.get(2), 103L, 1, 10), item(ids.get(0), 101L, 1, 20),
                item(null, 104L, 2, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.days[0].items[*].itemId", contains(ids.get(2).intValue(), ids.get(0).intValue())))
                .andExpect(jsonPath("$.days[0].placeCount").value(2))
                .andExpect(jsonPath("$.days[1].items[0].name").value("국립고궁박물관"))
                .andExpect(jsonPath("$.days[1].date").value(TODAY.plusDays(2).toString()));

        assertThat(jdbcTemplate.queryForList("SELECT sort_order FROM plan_items WHERE plan_id = ? AND day_number = 1 "
                + "ORDER BY sort_order", Integer.class, planId)).containsExactly(1, 2);
        assertThat(itemIds(planId)).contains(ids.get(0), ids.get(2)).doesNotContain(ids.get(1));
    }

    @Test
    @DisplayName("장소 0개·31개, 여행 기간 밖의 날, 다른 일정안의 itemId는 400 SCHEDULE_LIMIT_EXCEEDED, 메모 201자는 400 INVALID_INPUT")
    void validation() throws Exception {
        savePlan(token, tripId, planId, 0, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("일정안에는 장소를 1~30개 넣을 수 있습니다."));

        List<String> many = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            many.add(item(null, 101L, 1, i));
        }
        savePlan(token, tripId, planId, 0, String.join(",", many))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"));

        savePlan(token, tripId, planId, 0, items(item(null, 101L, 3, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"));

        long[] other = createTrip(token, "다른 여행", TODAY, TODAY);
        savePlan(token, other[0], other[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        long foreign = itemIds(other[1]).getFirst();
        savePlan(token, tripId, planId, 0, items(item(foreign, 101L, 1, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"));

        mockMvc.perform(json(put("/api/trips/{t}/plans/{p}", tripId, planId), token, """
                        {"version": 0, "name": "기본 코스", "items": [{"contentId": 101, "dayNumber": 1, "sortOrder": 1, "memo": "%s"}]}
                        """.formatted("가".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("메모는 200자까지 입력할 수 있습니다."));

        savePlan(token, tripId, planId, 0, items(item(null, 999L, 1, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
    }

    @Test
    @DisplayName("버전이 다르면 409 SCHEDULE_VERSION_CONFLICT이고 아무것도 바뀌지 않는다")
    void versionConflict() throws Exception {
        savePlan(token, tripId, planId, 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());

        savePlan(token, tripId, planId, 0, items(item(null, 102L, 1, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value("다른 기기에서 일정이 수정되었습니다. 최신 내용을 불러옵니다."));
        assertThat(jdbcTemplate.queryForList("SELECT content_id FROM plan_items WHERE plan_id = ?", Long.class, planId))
                .containsExactly(101L);
    }

    @Test
    @DisplayName("캘린더에 올린 항목의 시간·메모가 바뀌면 DIRTY, 그대로면 SYNCED. 빠진 올린 항목은 정리 이벤트, 확정안이면 변경 이벤트")
    void dirtyMarking() throws Exception {
        savePlan(token, tripId, planId, 0, items(item(null, 101L, 1, 1), item(null, 102L, 1, 2), item(null, 103L, 2, 1)))
                .andExpect(status().isOk());
        List<Long> ids = itemIds(planId);
        markSynced(ids.get(0), "evt0");
        markSynced(ids.get(1), "evt1");
        markSynced(ids.get(2), "evt2");
        events.clear();

        mockMvc.perform(json(put("/api/trips/{t}/plans/{p}", tripId, planId), token, """
                        {"version": 1, "name": "기본 코스", "items": [
                          {"itemId": %d, "contentId": 101, "dayNumber": 1, "sortOrder": 1},
                          {"itemId": %d, "contentId": 102, "dayNumber": 1, "sortOrder": 2, "startTime": "14:00", "memo": "점심 뒤"}
                        ]}
                        """.formatted(ids.get(0), ids.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].items[1].startTime").value("14:00"))
                .andExpect(jsonPath("$.days[0].items[0].calendarState").value("synced"))
                .andExpect(jsonPath("$.days[0].items[1].calendarState").value("changed"));

        assertThat(syncStatus(ids.get(0))).isEqualTo("SYNCED");
        assertThat(syncStatus(ids.get(1))).isEqualTo("DIRTY");
        assertThat(eventsOf(CalendarEventsOrphanedEvent.class)).singleElement()
                .satisfies(e -> assertThat(e.events()).extracting("eventId").containsExactly("evt2"));
        assertThat(eventsOf(ConfirmedPlanChangedEvent.class)).singleElement()
                .satisfies(e -> assertThat(e.planId()).isEqualTo(planId));
    }

    @Test
    @DisplayName("새로 넣은 장소만 SCHEDULE_SAVE 행동 신호를 낸다 (동행·여행 id 포함)")
    void scheduleSaveSignals() throws Exception {
        savePlan(token, tripId, planId, 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        long first = itemIds(planId).getFirst();
        events.clear();

        savePlan(token, tripId, planId, 1, items(item(first, 101L, 1, 1), item(null, 102L, 1, 2))).andExpect(status().isOk());

        List<BehaviorSignalEvent> signals = eventsOf(BehaviorSignalEvent.class);
        assertThat(signals).singleElement().satisfies(signal -> {
            assertThat(signal.contentId()).isEqualTo(102L);
            assertThat(signal.type()).isEqualTo(BehaviorSignalType.SCHEDULE_SAVE);
            assertThat(signal.companion()).isEqualTo("friends");
            assertThat(signal.tripId()).isEqualTo(tripId);
        });
    }

    @Test
    @DisplayName("이동은 경로 계산 캐시가 있으면 그 값을, 없으면 직선거리 어림값(isEstimated)을 쓰고 이동 시간 합계를 저장한다. 가까우면 도보")
    void routesFromCacheOrEstimate() throws Exception {
        redisTemplate.opsForHash().putAll(RouteRedisKeys.route(new LatLng(37.5796, 126.9770), new LatLng(37.5794, 126.9910)),
                Map.of("mode", "TRANSIT", "minutes", "12", "altMode", "TAXI", "altMinutes", "8",
                        "summary", "버스 7022", "polyline", "abc", "distance", "1300"));

        savePlan(token, tripId, planId, 0, items(item(null, 101L, 1, 1), item(null, 102L, 1, 2), item(null, 103L, 1, 3),
                item(null, 104L, 2, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].items[0].move").doesNotExist())
                .andExpect(jsonPath("$.days[0].items[1].move.mode").value("transit"))
                .andExpect(jsonPath("$.days[0].items[1].move.minutes").value(12))
                .andExpect(jsonPath("$.days[0].items[1].move.altMode").value("taxi"))
                .andExpect(jsonPath("$.days[0].items[1].move.summary").value("버스 7022"))
                .andExpect(jsonPath("$.days[0].items[1].move.isEstimated").value(false))
                .andExpect(jsonPath("$.days[0].items[2].move.isEstimated").value(true))
                .andExpect(jsonPath("$.days[1].items[0].move").doesNotExist());
        int estimated = jdbcTemplate.queryForObject(
                "SELECT travel_minutes FROM plan_items WHERE plan_id = ? AND content_id = 103", Integer.class, planId);
        assertThat(jdbcTemplate.queryForObject("SELECT total_move_minutes FROM plans WHERE id = ?", Integer.class, planId))
                .isEqualTo(12 + estimated);

        // 출발지가 있으면 첫째 날 첫 장소도 이동이 생긴다 (가까우면 도보)
        mockMvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/trips/{id}", tripId), token, """
                {"origin": {"name": "광화문", "lat": 37.5759, "lng": 126.9768}}
                """)).andExpect(status().isOk());
        List<Long> ids = itemIds(planId);
        savePlan(token, tripId, planId, 1, items(item(ids.get(0), 101L, 1, 1), item(ids.get(1), 102L, 1, 2)))
                .andExpect(jsonPath("$.days[0].items[0].move.mode").value("walk"))
                .andExpect(jsonPath("$.days[0].items[0].move.isEstimated").value(false))
                .andExpect(jsonPath("$.days[0].items[1].move.mode").value("transit"));
    }
}
