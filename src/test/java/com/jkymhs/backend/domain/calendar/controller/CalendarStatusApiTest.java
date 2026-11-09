package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarStatusApiTest extends CalendarApiTestSupport {

    @Test
    @DisplayName("연동하지 않았으면 linked=false, 나머지는 null·0·빈 목록")
    void notLinked() throws Exception {
        String token = token(saveUser("none@example.com"));

        mockMvc.perform(auth(get("/api/calendar"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.googleEmail").value(nullValue()))
                .andExpect(jsonPath("$.uploadedEventCount").value(0))
                .andExpect(jsonPath("$.trips").isEmpty());
    }

    @Test
    @DisplayName("연동 정보와 올린 일정 수, 일정이 있는 여행만 시작일 순으로 확정안 기준 상태(synced/changed)를 준다")
    void linked() throws Exception {
        loadPlaces();
        User user = saveUser("status@example.com");
        String token = token(user);
        link(user);
        long[] later = tripWith(token, TODAY.plusDays(10), TODAY.plusDays(10), items(item(null, 101L, 1, 1)));
        long[] sooner = tripWith(token, TODAY.plusDays(2), TODAY.plusDays(2),
                items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        tripWith(token, TODAY.plusDays(5), TODAY.plusDays(5), items(item(null, 103L, 1, 1)));
        uploaded(itemIds(later[1]).getFirst());
        List<Long> soonerItems = itemIds(sooner[1]);
        uploaded(soonerItems.get(0));
        uploaded(soonerItems.get(1));
        jdbcTemplate.update("UPDATE plan_items SET sync_status = 'DIRTY' WHERE id = ?", soonerItems.get(1));

        mockMvc.perform(auth(get("/api/calendar"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.googleEmail").value(GOOGLE_EMAIL))
                .andExpect(jsonPath("$.linkedAt").value("2026-11-09T10:00:00"))
                .andExpect(jsonPath("$.defaultCalendarId").value("primary"))
                .andExpect(jsonPath("$.uploadedEventCount").value(3))
                .andExpect(jsonPath("$.trips.length()").value(2))
                .andExpect(jsonPath("$.trips[0].tripId").value(sooner[0]))
                .andExpect(jsonPath("$.trips[0].sync").value("changed"))
                .andExpect(jsonPath("$.trips[1].tripId").value(later[0]))
                .andExpect(jsonPath("$.trips[1].title").value("부산 여행"))
                .andExpect(jsonPath("$.trips[1].sync").value("synced"));
    }

    @Test
    @DisplayName("만료된 연동은 linked=true, status=REVOKED로 보여준다 (화면이 다시 연동을 안내한다)")
    void revoked() throws Exception {
        User user = saveUser("revoked@example.com");
        link(user);
        jdbcTemplate.update("UPDATE google_calendar_links SET status = 'REVOKED'");

        mockMvc.perform(auth(get("/api/calendar"), token(user)))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }
}
