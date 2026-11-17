package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.event.CalendarEventRef;
import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripDeleteApiTest extends TripApiTestSupport {

    @Test
    @DisplayName("삭제하면 캘린더에 올린 일정들을 이벤트로 먼저 알리고, 방문 확인·항목·일정안·지역·여행을 모두 지운다")
    void deleteTrip() throws Exception {
        String token = token(saveUser("delete@example.com"));
        long[] ids = createTrip(token, "지난 여행", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910));
        savePlan(token, ids[0], ids[1], 0, items(item(null, 101L, 1, 1), item(null, 102L, 2, 1)))
                .andExpect(status().isOk());
        List<Long> itemIds = itemIds(ids[1]);
        markSynced(itemIds.get(0), "evt1");
        mockMvc.perform(json(put("/api/trips/{id}/visit-check", ids[0]), token, """
                {"items": [{"itemId": %d, "visited": false, "rating": 0}]}
                """.formatted(itemIds.get(1)))).andExpect(status().isOk());

        mockMvc.perform(auth(delete("/api/trips/{id}", ids[0]), token)).andExpect(status().isNoContent());

        List<CalendarEventsOrphanedEvent> orphaned = eventsOf(CalendarEventsOrphanedEvent.class);
        assertThat(orphaned).hasSize(1);
        assertThat(orphaned.getFirst().tripId()).isEqualTo(ids[0]);
        assertThat(orphaned.getFirst().events()).containsExactly(new CalendarEventRef("primary", "evt1"));
        for (String table : List.of("trips", "trip_regions", "plans", "plan_items", "visit_reviews")) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).as(table).isZero();
        }
    }

    @Test
    @DisplayName("다른 회원의 여행은 지울 수 없다 (403)")
    void othersTrip() throws Exception {
        long[] ids = createTrip(token(saveUser("owner@example.com")), "여행", TODAY, TODAY);
        mockMvc.perform(auth(delete("/api/trips/{id}", ids[0]), token(saveUser("x@example.com"))))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trips", Long.class)).isEqualTo(1);
    }
}
