package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripDetailApiTest extends TripApiTestSupport {

    @Test
    @DisplayName("다른 회원의 여행은 403 FORBIDDEN, 일정안도 403")
    void othersTrip() throws Exception {
        long[] ids = createTrip(token(saveUser("owner@example.com")), "남의 여행", TODAY, TODAY);
        String stranger = token(saveUser("stranger@example.com"));

        mockMvc.perform(auth(get("/api/trips/{id}", ids[0]), stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
        mockMvc.perform(auth(get("/api/trips/{id}/plans/{planId}", ids[0], ids[1]), stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 여행은 404 \"존재하지 않는 여행입니다.\", 다른 여행의 일정안 id는 404")
    void notFound() throws Exception {
        String token = token(saveUser("me@example.com"));
        long[] mine = createTrip(token, "내 여행", TODAY, TODAY);
        long[] other = createTrip(token, "다른 여행", TODAY, TODAY);

        mockMvc.perform(auth(get("/api/trips/{id}", 999999), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
        mockMvc.perform(auth(get("/api/trips/{id}/plans/{planId}", mine[0], other[1]), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 일정안입니다."));
    }
}
