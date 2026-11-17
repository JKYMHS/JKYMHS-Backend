package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarJobStatusApiTest extends CalendarApiTestSupport {

    private User user;
    private String token;
    private long[] trip;
    private List<Long> ids;

    @BeforeEach
    void setUp() throws Exception {
        loadPlaces();
        user = saveUser("job@example.com");
        token = token(user);
        link(user);
        trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1),
                items(timed(101L, 1, 1, "09:00", "10:00", null), item(null, 102L, 1, 2)));
        ids = itemIds(trip[1]);
        mockMvc.perform(json(post("/api/trips/{id}/calendar-uploads", trip[0]), token, "{}"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("진행 중이면 PENDING과 pollAfterMs를 준다")
    void pending() throws Exception {
        mockMvc.perform(auth(get("/api/calendar/jobs/{id}", latestJobId()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("UPLOAD"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.pollAfterMs").value(2000));
    }

    @Test
    @DisplayName("끝나면 pollAfterMs가 빠지고, 실패 항목(이름·시각·이유)과 여행별 n/m, 다시 올리기 가능 여부를 준다")
    void finished() throws Exception {
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"code\": 403, \"errors\": [{\"reason\": \"requiredAccessLevel\"}]}}"));
        worker.runOnce();

        mockMvc.perform(auth(get("/api/calendar/jobs/{id}", latestJobId()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILED"))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.failedItems[0].itemId").value(ids.get(1)))
                .andExpect(jsonPath("$.failedItems[0].tripId").value(trip[0]))
                .andExpect(jsonPath("$.failedItems[0].name").value("창덕궁"))
                .andExpect(jsonPath("$.failedItems[0].startLabel").value("11.10 종일"))
                .andExpect(jsonPath("$.failedItems[0].reasonCode").value("NOT_WRITABLE"))
                .andExpect(jsonPath("$.failedItems[0].reason").value("선택한 캘린더에 일정을 쓸 수 없습니다."))
                .andExpect(jsonPath("$.trips[0].title").value("부산 여행"))
                .andExpect(jsonPath("$.trips[0].succeeded").value(1))
                .andExpect(jsonPath("$.trips[0].total").value(2))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.pollAfterMs").doesNotExist());
    }

    @Test
    @DisplayName("다른 회원의 작업이나 없는 작업은 404 \"존재하지 않는 작업입니다.\"")
    void othersJob() throws Exception {
        mockMvc.perform(auth(get("/api/calendar/jobs/{id}", latestJobId()), token(saveUser("x@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 작업입니다."));
        mockMvc.perform(auth(get("/api/calendar/jobs/{id}", 999999L), token))
                .andExpect(status().isNotFound());
    }
}
