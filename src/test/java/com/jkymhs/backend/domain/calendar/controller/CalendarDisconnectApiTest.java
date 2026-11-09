package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarDisconnectApiTest extends CalendarApiTestSupport {

    @Test
    @DisplayName("그대로 두기: 토큰을 폐기하고 연동을 지우고, 항목·여행의 캘린더 정보를 비운다 (작업 없음)")
    void keepEvents() throws Exception {
        loadPlaces();
        User user = saveUser("keep@example.com");
        String token = token(user);
        link(user);
        long[] trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1), items(item(null, 101L, 1, 1)));
        long itemId = itemIds(trip[1]).getFirst();
        uploaded(itemId);
        jdbcTemplate.update("UPDATE trips SET calendar_id = 'primary' WHERE id = ?", trip[0]);
        expectRevoke();

        mockMvc.perform(auth(delete("/api/calendar").param("deleteEvents", "false"), token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(nullValue()));

        google.verify();
        assertThat(linkCount()).isZero();
        assertThat(jobCount()).isZero();
        assertThat(syncStatus(itemId)).isEqualTo("NONE");
        assertThat(jdbcTemplate.queryForMap("SELECT calendar_event_id, calendar_id FROM plan_items WHERE id = ?", itemId))
                .containsEntry("calendar_event_id", null).containsEntry("calendar_id", null);
        assertThat(jdbcTemplate.queryForObject("SELECT calendar_id FROM trips WHERE id = ?", String.class, trip[0]))
                .isNull();
    }

    @Test
    @DisplayName("모두 지우기: DELETE_ALL 작업(토큰 사본·끝나면 폐기)을 넣고, 작업자가 일정을 지운 뒤 연동을 지운다. 이미 없는 일정(404)도 성공이다")
    void deleteEvents() throws Exception {
        loadPlaces();
        User user = saveUser("delete@example.com");
        String token = token(user);
        GoogleCalendarLink link = link(user);
        long[] trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1),
                items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        List<Long> itemIds = itemIds(trip[1]);
        itemIds.forEach(this::uploaded);

        long jobId = ((Number) com.jayway.jsonpath.JsonPath.read(body(mockMvc.perform(
                        auth(delete("/api/calendar").param("deleteEvents", "true"), token))
                .andExpect(status().isAccepted()).andReturn()), "$.jobId")).longValue();

        Map<String, Object> job = job(jobId);
        assertThat(job).containsEntry("job_type", "DELETE_ALL").containsEntry("status", "PENDING")
                .containsEntry("total", 2).containsEntry("revoke_after", true)
                .containsEntry("encrypted_refresh_token", link.getEncryptedRefreshToken());
        assertThat(linkCount()).as("작업이 끝날 때까지는 연동을 남긴다").isEqualTo(1);

        expectRefresh();
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(itemIds.get(0)))).andRespond(withNoContent());
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(itemIds.get(1)))).andRespond(withResourceNotFound());
        expectRevoke();

        assertThat(worker.runOnce()).isEqualTo(1);

        google.verify();
        assertThat(job(jobId)).containsEntry("status", "DONE").containsEntry("succeeded", 2);
        assertThat(linkCount()).isZero();
        itemIds.forEach(id -> assertThat(syncStatus(id)).isEqualTo("NONE"));
    }

    @Test
    @DisplayName("연동이 없으면 404 \"연동된 캘린더가 없습니다.\"")
    void notLinked() throws Exception {
        mockMvc.perform(auth(delete("/api/calendar"), token(saveUser("none@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("연동된 캘린더가 없습니다."));
    }

    private void expectRevoke() {
        google.expect(requestTo(REVOKE_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("token=" + REFRESH_TOKEN)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
    }
}
