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

class CalendarPreviewApiTest extends CalendarApiTestSupport {

    @Test
    @DisplayName("확정안의 날짜별 항목과 시간·길이·종일 여부·상태(new/changed/synced/failed)를 준다")
    void preview() throws Exception {
        loadPlaces();
        User user = saveUser("preview@example.com");
        String token = token(user);
        long[] trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(3), items(
                timed(101L, 1, 1, "09:00", "11:00", "아침 일찍"),
                timed(102L, 1, 2, null, null, null),
                timed(103L, 2, 1, "22:00", "01:00", null),
                timed(101L, 2, 2, "13:00", null, null)));
        List<Long> ids = itemIds(trip[1]);
        uploaded(ids.get(0));
        uploaded(ids.get(1));
        jdbcTemplate.update("UPDATE plan_items SET sync_status = 'DIRTY' WHERE id = ?", ids.get(1));
        jdbcTemplate.update("UPDATE plan_items SET sync_status = 'FAILED' WHERE id = ?", ids.get(2));

        mockMvc.perform(auth(get("/api/trips/{id}/calendar-preview", trip[0]), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip[0]))
                .andExpect(jsonPath("$.planId").value(trip[1]))
                .andExpect(jsonPath("$.planName").value("기본 코스"))
                .andExpect(jsonPath("$.calendarId").value("primary"))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value(TODAY.plusDays(1).toString()))
                .andExpect(jsonPath("$.days[0].items[0].itemId").value(ids.get(0)))
                .andExpect(jsonPath("$.days[0].items[0].name").value("경복궁"))
                .andExpect(jsonPath("$.days[0].items[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.days[0].items[0].endTime").value("11:00"))
                .andExpect(jsonPath("$.days[0].items[0].durationMinutes").value(120))
                .andExpect(jsonPath("$.days[0].items[0].allDay").value(false))
                .andExpect(jsonPath("$.days[0].items[0].state").value("synced"))
                .andExpect(jsonPath("$.days[0].items[1].allDay").value(true))
                .andExpect(jsonPath("$.days[0].items[1].durationMinutes").value(nullValue()))
                .andExpect(jsonPath("$.days[0].items[1].state").value("changed"))
                .andExpect(jsonPath("$.days[1].items[0].durationMinutes").value(180))
                .andExpect(jsonPath("$.days[1].items[0].state").value("failed"))
                .andExpect(jsonPath("$.days[1].items[1].state").value("new"))
                .andExpect(jsonPath("$.days[2].items").isEmpty());
    }

    @Test
    @DisplayName("기본 캘린더는 이 여행에 마지막으로 쓴 캘린더, 없으면 연동의 기본 캘린더다")
    void calendarDefault() throws Exception {
        User user = saveUser("cal@example.com");
        String token = token(user);
        link(user);
        jdbcTemplate.update("UPDATE google_calendar_links SET default_calendar_id = 'family@group'");
        long[] trip = createTrip(token, "여행", TODAY.plusDays(1), TODAY.plusDays(1));

        mockMvc.perform(auth(get("/api/trips/{id}/calendar-preview", trip[0]), token))
                .andExpect(jsonPath("$.calendarId").value("family@group"));
        jdbcTemplate.update("UPDATE trips SET calendar_id = 'trip@group'");
        mockMvc.perform(auth(get("/api/trips/{id}/calendar-preview", trip[0]), token))
                .andExpect(jsonPath("$.calendarId").value("trip@group"));
    }

    @Test
    @DisplayName("확정안이 없으면 400 PLAN_NOT_CONFIRMED, 다른 회원의 여행은 403")
    void errors() throws Exception {
        String token = token(saveUser("owner@example.com"));
        long[] trip = createTrip(token, "여행", TODAY.plusDays(1), TODAY.plusDays(1));

        mockMvc.perform(auth(get("/api/trips/{id}/calendar-preview", trip[0]), token(saveUser("x@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        jdbcTemplate.update("UPDATE trips SET confirmed_plan_id = NULL");
        mockMvc.perform(auth(get("/api/trips/{id}/calendar-preview", trip[0]), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.message").value("확정된 일정안이 없습니다. 일정안을 먼저 확정해주세요."));
    }
}
