package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.user.entity.User;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 도메인 이벤트 → 캘린더 작업 (자동 동기화·삭제·탈퇴).
 */
class CalendarAutoSyncListenerTest extends CalendarApiTestSupport {

    private User user;
    private String token;
    private long[] trip;
    private List<Long> ids;

    @BeforeEach
    void setUp() throws Exception {
        loadPlaces();
        user = saveUser("auto@example.com");
        token = token(user);
        trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1), items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        ids = itemIds(trip[1]);
        ids.forEach(this::uploaded);
    }

    @Test
    @DisplayName("캘린더에 올린 확정안을 저장하면 커밋 뒤 SYNC 작업: 바뀐 항목은 UPDATE, 새 항목은 CREATE, 그대로인 항목은 빠진다")
    void planSaveCreatesSync() throws Exception {
        link(user);

        savePlan(token, trip[0], trip[1], 1, items(
                timed(ids.get(0), 101L, 1, 1, "10:00"),
                item(ids.get(1), 102L, 1, 2),
                item(null, 103L, 1, 3))).andExpect(status().isOk());

        long jobId = latestJobId();
        assertThat(job(jobId)).containsEntry("job_type", "SYNC").containsEntry("trip_id", trip[0])
                .containsEntry("status", "PENDING").containsEntry("total", 2);
        List<Long> after = itemIds(trip[1]);
        assertThat(jobItems(jobId)).extracting(row -> row.get("plan_item_id"), row -> row.get("operation"))
                .containsExactly(Tuple.tuple(ids.get(0), "UPDATE"), Tuple.tuple(after.get(2), "CREATE"));
        assertThat(syncStatus(ids.get(0))).isEqualTo("PENDING");
        assertThat(syncStatus(ids.get(1))).isEqualTo("SYNCED");
        assertThat(syncStatus(after.get(2))).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("연동이 없거나 만료면 자동 작업을 만들지 않는다 (항목은 DIRTY로 남아 화면에 \"변경됨\")")
    void noLinkNoJob() throws Exception {
        savePlan(token, trip[0], trip[1], 1, items(timed(ids.get(0), 101L, 1, 1, "10:00"), item(ids.get(1), 102L, 1, 2)))
                .andExpect(status().isOk());
        mockMvc.perform(auth(delete("/api/trips/{id}", trip[0]), token)).andExpect(status().isNoContent());

        assertThat(jobCount()).isZero();
    }

    @Test
    @DisplayName("저장에서 항목을 빼면 그 일정 삭제 작업(DELETE_EVENTS)이 먼저 들고, 그 작업이 끝나면 남은 DIRTY 항목을 SYNC로 맞춘다")
    void removedItemThenRescan() throws Exception {
        link(user);

        savePlan(token, trip[0], trip[1], 1, items(timed(ids.get(0), 101L, 1, 1, "10:00"))).andExpect(status().isOk());

        assertThat(jobCount()).as("같은 여행에 진행 중인 작업이 있어서 SYNC는 미룬다").isEqualTo(1);
        long deleteJob = latestJobId();
        assertThat(job(deleteJob)).containsEntry("job_type", "DELETE_EVENTS");
        assertThat(jobItems(deleteJob)).singleElement().satisfies(row -> {
            assertThat(row.get("operation")).isEqualTo("DELETE");
            assertThat(row.get("event_id")).isEqualTo(eventId(ids.get(1)));
            assertThat(row.get("plan_item_id")).isEqualTo(ids.get(1));
        });
        assertThat(syncStatus(ids.get(0))).isEqualTo("DIRTY");

        expectRefresh();
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(1)))).andRespond(withNoContent());
        worker.runOnce();

        google.verify();
        long syncJob = latestJobId();
        assertThat(syncJob).isNotEqualTo(deleteJob);
        assertThat(job(syncJob)).containsEntry("job_type", "SYNC");
        assertThat(jobItems(syncJob)).singleElement()
                .satisfies(row -> assertThat(row.get("plan_item_id")).isEqualTo(ids.get(0)));
    }

    @Test
    @DisplayName("확정안을 바꾸면 같은 트랜잭션에서 예전 확정안 일정 DELETE + 새 확정안 항목 CREATE 작업을 넣는다")
    void switchConfirmedPlan() throws Exception {
        link(user);
        long second = longAt(mockMvc.perform(auth(post("/api/trips/{id}/plans", trip[0]), token))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        savePlan(token, trip[0], second, 0, items(item(null, 103L, 1, 1))).andExpect(status().isOk());

        mockMvc.perform(json(put("/api/trips/{id}/confirmed-plan", trip[0]), token, """
                {"planId": %d}""".formatted(second))).andExpect(status().isOk());

        long jobId = latestJobId();
        assertThat(job(jobId)).containsEntry("job_type", "SYNC");
        long newItem = itemIds(second).getFirst();
        assertThat(jobItems(jobId)).extracting(row -> row.get("operation"), row -> row.get("event_id"))
                .containsExactly(Tuple.tuple("DELETE", eventId(ids.get(0))), Tuple.tuple("DELETE", eventId(ids.get(1))),
                        Tuple.tuple("CREATE", eventId(newItem)));

        // 작업자가 예전 일정을 지우면 예전 확정안 항목의 캘린더 정보도 비운다 (다시 확정하면 새로 올릴 수 있게)
        expectRefresh();
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(0)))).andRespond(withNoContent());
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(1)))).andRespond(withNoContent());
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        worker.runOnce();

        google.verify();
        assertThat(syncStatus(ids.get(0))).isEqualTo("NONE");
        assertThat(syncStatus(newItem)).isEqualTo("SYNCED");
    }

    @Test
    @DisplayName("여행을 지우면 올린 일정들의 DELETE_EVENTS 작업을 넣는다 (여행이 지워져도 작업은 남는다)")
    void tripDelete() throws Exception {
        link(user);

        mockMvc.perform(auth(delete("/api/trips/{id}", trip[0]), token)).andExpect(status().isNoContent());

        long jobId = latestJobId();
        assertThat(job(jobId)).containsEntry("job_type", "DELETE_EVENTS").containsEntry("trip_id", trip[0])
                .containsEntry("total", 2);
        assertThat(jobItems(jobId)).extracting(row -> row.get("event_id"))
                .containsExactly(eventId(ids.get(0)), eventId(ids.get(1)));
    }

    @Test
    @DisplayName("탈퇴: 요약에 올린 일정 수가 나오고, 탈퇴하면 토큰 사본을 담은 DELETE_ALL 작업 하나만 넣고 연동을 지운다. 작업자가 일정을 지운 뒤 토큰을 폐기한다")
    void withdraw() throws Exception {
        GoogleCalendarLink link = link(user);
        mockMvc.perform(auth(get("/api/members/me/withdraw-summary"), token))
                .andExpect(jsonPath("$.calendarEventCount").value(2));

        mockMvc.perform(json(delete("/api/members/me"), token, """
                {"password": "%s"}""".formatted(PASSWORD))).andExpect(status().isNoContent());

        assertThat(jobCount()).isEqualTo(1);
        Map<String, Object> job = job(latestJobId());
        assertThat(job).containsEntry("job_type", "DELETE_ALL").containsEntry("user_id", user.getId())
                .containsEntry("revoke_after", true).containsEntry("total", 2)
                .containsEntry("encrypted_refresh_token", link.getEncryptedRefreshToken());
        assertThat(linkCount()).isZero();
        assertThat(userRepository.findById(user.getId())).isEmpty();

        expectRefresh();
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(0)))).andRespond(withNoContent());
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(1)))).andRespond(withNoContent());
        google.expect(requestTo(REVOKE_URI)).andExpect(content().string(containsString("token=" + REFRESH_TOKEN)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        worker.runOnce();

        google.verify();
        assertThat(job(latestJobId())).containsEntry("status", "DONE").containsEntry("succeeded", 2);
    }

    private static String timed(Long itemId, long contentId, int day, int sort, String start) {
        return """
                {"itemId": %s, "contentId": %d, "dayNumber": %d, "sortOrder": %d, "startTime": "%s"}"""
                .formatted(itemId, contentId, day, sort, start);
    }
}
