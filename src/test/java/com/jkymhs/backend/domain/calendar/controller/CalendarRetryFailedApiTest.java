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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarRetryFailedApiTest extends CalendarApiTestSupport {

    private User user;
    private String token;
    private List<Long> ids;

    @BeforeEach
    void setUp() throws Exception {
        loadPlaces();
        user = saveUser("retry@example.com");
        token = token(user);
        link(user);
        long[] trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1),
                items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        ids = itemIds(trip[1]);
        mockMvc.perform(json(post("/api/trips/{id}/calendar-uploads", trip[0]), token, "{}"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("실패한 항목만 새 작업으로 다시 올린다 (원래 작업은 그대로)")
    void retry() throws Exception {
        long first = latestJobId();
        runWithSecondForbidden();

        long retried = ((Number) com.jayway.jsonpath.JsonPath.read(body(mockMvc.perform(
                        auth(post("/api/calendar/jobs/{id}/retry-failed", first), token))
                .andExpect(status().isAccepted()).andReturn()), "$.jobId")).longValue();

        assertThat(retried).isNotEqualTo(first);
        assertThat(job(retried)).containsEntry("job_type", "UPLOAD").containsEntry("total", 1)
                .containsEntry("status", "PENDING");
        assertThat(jobItems(retried)).singleElement().satisfies(row -> {
            assertThat(row.get("plan_item_id")).isEqualTo(ids.get(1));
            assertThat(row.get("operation")).isEqualTo("CREATE");
        });
        assertThat(job(first)).containsEntry("status", "PARTIAL_FAILED");
        assertThat(syncStatus(ids.get(1))).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("실패 항목이 없으면 400 \"다시 올릴 항목이 없습니다.\", 아직 진행 중인 작업이면 409, 남의 작업이면 404")
    void errors() throws Exception {
        long first = latestJobId();
        mockMvc.perform(auth(post("/api/calendar/jobs/{id}/retry-failed", first), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALENDAR_SYNC_IN_PROGRESS"));
        mockMvc.perform(auth(post("/api/calendar/jobs/{id}/retry-failed", first), token(saveUser("x@example.com"))))
                .andExpect(status().isNotFound());

        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        worker.runOnce();

        mockMvc.perform(auth(post("/api/calendar/jobs/{id}/retry-failed", first), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("다시 올릴 항목이 없습니다."));
    }

    private void runWithSecondForbidden() {
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        worker.runOnce();
    }
}
