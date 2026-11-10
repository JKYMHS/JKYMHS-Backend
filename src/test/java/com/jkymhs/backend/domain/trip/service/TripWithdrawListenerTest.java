package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripWithdrawListenerTest extends TripApiTestSupport {

    @Test
    @DisplayName("탈퇴 요약에 여행·일정안 수가 나오고, 탈퇴하면 캘린더 정리 이벤트를 낸 뒤 그 회원의 여행 데이터만 모두 지운다")
    void withdrawDeletesTrips() throws Exception {
        load(place(101L, "경복궁", 37.5796, 126.9770));
        User user = saveUser("leave@example.com");
        String token = token(user);
        long[] first = createTrip(token, "지난 여행", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        long[] second = createTrip(token, "다음 여행", TODAY.plusDays(3), TODAY.plusDays(4));
        mockMvc.perform(auth(post("/api/trips/{id}/plans", second[0]), token)).andExpect(status().isCreated());
        savePlan(token, first[0], first[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        markSynced(itemIds(first[1]).getFirst(), "evt1");
        mockMvc.perform(json(put("/api/trips/{id}/visit-check", first[0]), token, """
                {"items": [{"itemId": %d, "visited": true, "rating": 4}]}
                """.formatted(itemIds(first[1]).getFirst()))).andExpect(status().isOk());

        String otherToken = token(saveUser("stay@example.com"));
        long[] kept = createTrip(otherToken, "남는 여행", TODAY, TODAY);
        savePlan(otherToken, kept[0], kept[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/members/me/withdraw-summary"), token))
                .andExpect(jsonPath("$.tripCount").value(2))
                .andExpect(jsonPath("$.planCount").value(3));

        mockMvc.perform(json(delete("/api/members/me"), token, """
                {"password": "%s"}
                """.formatted(PASSWORD))).andExpect(status().isNoContent());

        List<CalendarEventsOrphanedEvent> orphaned = eventsOf(CalendarEventsOrphanedEvent.class);
        assertThat(orphaned).singleElement().satisfies(e -> {
            assertThat(e.tripId()).isEqualTo(first[0]);
            assertThat(e.events()).extracting("eventId").containsExactly("evt1");
        });
        assertThat(jdbcTemplate.queryForList("SELECT id FROM trips", Long.class)).containsExactly(kept[0]);
        assertThat(jdbcTemplate.queryForList("SELECT trip_id FROM plans", Long.class)).containsExactly(kept[0]);
        assertThat(jdbcTemplate.queryForList("SELECT trip_id FROM trip_regions", Long.class)).containsExactly(kept[0]);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_items", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM visit_reviews", Long.class)).isZero();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
