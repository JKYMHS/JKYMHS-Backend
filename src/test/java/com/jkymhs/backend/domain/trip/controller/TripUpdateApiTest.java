package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripUpdateApiTest extends TripApiTestSupport {

    private String token;
    private long tripId;
    private long planId;

    @BeforeEach
    void setUp() throws Exception {
        token = token(saveUser("update@example.com"));
        long[] ids = createTrip(token, "3일 여행", TODAY.plusDays(1), TODAY.plusDays(3));
        tripId = ids[0];
        planId = ids[1];
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910),
                place(103L, "덕수궁", 37.5658, 126.9751));
        savePlan(token, tripId, planId, 0, items(item(null, 101L, 1, 1), item(null, 102L, 2, 1),
                item(null, 103L, 3, 1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기간을 줄여서 장소가 빠지면 422(description = 빠지는 장소 수), removeOutOfRangeItems=true면 지우고 버전을 올린다")
    void shrink() throws Exception {
        markSynced(itemIds(planId).get(2), "evt-day3");

        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"endDate": "%s"}
                        """.formatted(TODAY.plusDays(1))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLAN_EXCEEDS_TRIP_PERIOD"))
                .andExpect(jsonPath("$.description").value("2"));
        assertThat(itemIds(planId)).hasSize(3);

        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"endDate": "%s", "removeOutOfRangeItems": true}
                        """.formatted(TODAY.plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(TODAY.plusDays(1).toString()))
                .andExpect(jsonPath("$.plans[0].placeCount").value(1))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(itemIds(planId)).hasSize(1);
        assertThat(planVersion(token, tripId, planId)).isEqualTo(2);
        List<CalendarEventsOrphanedEvent> orphaned = eventsOf(CalendarEventsOrphanedEvent.class);
        assertThat(orphaned).hasSize(1);
        assertThat(orphaned.getFirst().events()).extracting("eventId").containsExactly("evt-day3");
    }

    @Test
    @DisplayName("이름·동행·출발지를 바꾸고, 출발지를 지울 수 있다")
    void fields() throws Exception {
        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"title": "새 이름", "companion": "parents", "regionIds": [%d],
                         "origin": {"name": "집", "lat": 37.5, "lng": 127.0}}
                        """.formatted(busanId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 이름"))
                .andExpect(jsonPath("$.companion").value("parents"))
                .andExpect(jsonPath("$.regions[0].name").value("부산"))
                .andExpect(jsonPath("$.origin.name").value("집"));

        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"clearOrigin": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").doesNotExist());

        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"companion": "kids"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아이 나이를 선택해주세요."));
    }

    @Test
    @DisplayName("가는 날을 지난 날짜로 바꾸면 400이고, 가는 날을 옮기면 캘린더에 올린 항목이 DIRTY가 된다")
    void moveStart() throws Exception {
        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"startDate": "%s"}
                        """.formatted(TODAY.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지난 날짜는 선택할 수 없습니다."));

        long first = itemIds(planId).getFirst();
        markSynced(first, "evt1");
        events.clear();
        mockMvc.perform(json(patch("/api/trips/{id}", tripId), token, """
                        {"startDate": "%s", "endDate": "%s"}
                        """.formatted(TODAY.plusDays(2), TODAY.plusDays(4))))
                .andExpect(status().isOk());
        assertThat(syncStatus(first)).isEqualTo("DIRTY");
        assertThat(eventsOf(ConfirmedPlanChangedEvent.class)).hasSize(1);
    }
}
