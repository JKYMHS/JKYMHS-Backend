package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripTargetsApiTest extends TripApiTestSupport {

    private String token;
    private long shortTrip;   // 11-12 ~ 11-13 (2일)
    private long longTrip;    // 11-20 ~ 11-23 (4일)

    @BeforeEach
    void setUp() throws Exception {
        token = token(saveUser("targets@example.com"));
        longTrip = createTrip(token, "긴 여행", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23))[0];
        shortTrip = createTrip(token, "짧은 여행", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13))[0];
        createTrip(token, "지난 여행", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        load(festival(201L, "단풍 축제", 37.57, 126.98, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 30)),
                place(101L, "경복궁", 37.5796, 126.9770));
    }

    @Test
    @DisplayName("다가오는 여행만 시작일 순이고, 축제 기간과 겹치는 여행이 기본 선택이다")
    void festivalOverlap() throws Exception {
        mockMvc.perform(auth(get("/api/trips/targets").param("contentId", "201"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) shortTrip, (int) longTrip)))
                .andExpect(jsonPath("$.items[*].overlaps", contains(false, true)))
                .andExpect(jsonPath("$.items[1].days.length()").value(4))
                .andExpect(jsonPath("$.items[1].days[1].date").value("2026-11-21"))
                .andExpect(jsonPath("$.items[0].plans[0].isConfirmed").value(true))
                .andExpect(jsonPath("$.items[0].planCount").value(1))
                .andExpect(jsonPath("$.defaultTripId").value(longTrip));
    }

    @Test
    @DisplayName("날짜가 같은 여행이 기본 선택이고, 고른 일수보다 짧은 여행은 selectable=false")
    void dateMatched() throws Exception {
        mockMvc.perform(auth(get("/api/trips/targets").param("contentId", "101")
                        .param("startDate", "2026-11-20").param("endDate", "2026-11-22"), token))
                .andExpect(jsonPath("$.items[*].dateMatched", contains(false, false)))
                .andExpect(jsonPath("$.items[*].selectable", contains(false, true)))
                .andExpect(jsonPath("$.defaultTripId").value(nullValue()));

        mockMvc.perform(auth(get("/api/trips/targets")
                        .param("startDate", "2026-11-12").param("endDate", "2026-11-13"), token))
                .andExpect(jsonPath("$.items[*].dateMatched", contains(true, false)))
                .andExpect(jsonPath("$.items[*].selectable", contains(true, true)))
                .andExpect(jsonPath("$.defaultTripId").value(shortTrip));
    }

    @Test
    @DisplayName("없는 장소면 404")
    void unknownContent() throws Exception {
        mockMvc.perform(auth(get("/api/trips/targets").param("contentId", "999"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
    }
}
