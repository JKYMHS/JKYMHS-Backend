package com.jkymhs.backend.domain.calendar.worker;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작업자: 업로드 API로 작업을 넣고 runOnce()로 한 차례씩 돌린다. 백오프는 MutableClock을 옮겨서 확인한다 (실제로 기다리지 않는다).
 */
class CalendarSyncWorkerTest extends CalendarApiTestSupport {

    private static final String FAMILY = "family@group.calendar.google.com";
    private static final String FAMILY_ENCODED = "family%40group.calendar.google.com";

    private User user;
    private String token;
    private long[] trip;

    @BeforeEach
    void setUp() {
        loadPlaces();
        user = saveUser("worker@example.com");
        token = token(user);
        link(user);
    }

    @Test
    @DisplayName("새 일정: 제목·장소·메모+상세 링크·시각(KST)으로 만들고 항목을 SYNCED로, 처음 올린 장소마다 CALENDAR 신호를 낸다")
    void create() throws Exception {
        List<Long> ids = tripAndUpload(items(timed(101L, 1, 1, "09:00", "11:30", "아침 일찍"),
                timed(102L, 1, 2, null, null, null)), "{}");
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary"))
                .andExpect(jsonPath("$.id").value(eventId(ids.get(0))))
                .andExpect(jsonPath("$.summary").value("[어디 갈래] 경복궁"))
                .andExpect(jsonPath("$.location").value("주소 경복궁"))
                .andExpect(jsonPath("$.description").value("아침 일찍\nhttp://localhost:3000/places/101"))
                .andExpect(jsonPath("$.start.dateTime").value("2026-11-10T09:00:00"))
                .andExpect(jsonPath("$.start.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.end.dateTime").value("2026-11-10T11:30:00"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.POST, events("primary"))
                .andExpect(jsonPath("$.start.date").value("2026-11-10"))
                .andExpect(jsonPath("$.start.dateTime").value(nullValue()))
                .andExpect(jsonPath("$.end.date").value("2026-11-11"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(worker.runOnce()).isEqualTo(1);

        google.verify();
        assertThat(job(latestJobId())).containsEntry("status", "DONE").containsEntry("succeeded", 2);
        assertThat(jdbcTemplate.queryForMap("SELECT sync_status, calendar_id, calendar_event_id, synced_at FROM plan_items "
                + "WHERE id = ?", ids.get(0)))
                .containsEntry("sync_status", "SYNCED").containsEntry("calendar_id", "primary")
                .containsEntry("calendar_event_id", eventId(ids.get(0)));
        List<BehaviorSignalEvent> signals = eventsOf(BehaviorSignalEvent.class).stream()
                .filter(e -> e.type() == BehaviorSignalType.CALENDAR).toList();
        assertThat(signals).extracting(BehaviorSignalEvent::contentId).containsExactly(101L, 102L);
        assertThat(signals.getFirst().tripId()).isEqualTo(trip[0]);
        assertThat(signals.getFirst().companion()).isEqualTo("friends");
        assertThat(signals.getFirst().userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("이미 올린 일정은 PUT으로 고치고(신호 없음), 구글에서 사라졌으면(404) 새로 만든다")
    void updateAndRecreate() throws Exception {
        List<Long> ids = tripWithIds(items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        ids.forEach(this::uploaded);
        upload("{\"itemIds\": [%d, %d]}".formatted(ids.get(0), ids.get(1)));
        expectRefresh();
        expectGoogle(HttpMethod.PUT, event("primary", eventId(ids.get(0))))
                .andExpect(jsonPath("$.summary").value("[어디 갈래] 경복궁"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.PUT, event("primary", eventId(ids.get(1)))).andRespond(withResourceNotFound());
        expectGoogle(HttpMethod.POST, events("primary")).andExpect(jsonPath("$.id").value(eventId(ids.get(1))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        worker.runOnce();

        google.verify();
        assertThat(job(latestJobId())).containsEntry("status", "DONE");
        ids.forEach(id -> assertThat(syncStatus(id)).isEqualTo("SYNCED"));
        assertThat(eventsOf(BehaviorSignalEvent.class)).noneMatch(e -> e.type() == BehaviorSignalType.CALENDAR);
    }

    @Test
    @DisplayName("같은 id의 일정이 있으면(409) 읽어서, 지워진(cancelled) 일정이면 PATCH로 되살리고 아니면 PUT으로 고친다")
    void conflictRevive() throws Exception {
        List<Long> ids = tripAndUpload(items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)), "{}");
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withStatus(HttpStatus.CONFLICT));
        expectGoogle(HttpMethod.GET, event("primary", eventId(ids.get(0))))
                .andRespond(withSuccess("{\"id\": \"x\", \"status\": \"cancelled\"}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.PATCH, event("primary", eventId(ids.get(0))))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.summary").value("[어디 갈래] 경복궁"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withStatus(HttpStatus.CONFLICT));
        expectGoogle(HttpMethod.GET, event("primary", eventId(ids.get(1))))
                .andRespond(withSuccess("{\"id\": \"x\", \"status\": \"confirmed\"}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.PUT, event("primary", eventId(ids.get(1))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        worker.runOnce();

        google.verify();
        ids.forEach(id -> assertThat(syncStatus(id)).isEqualTo("SYNCED"));
    }

    @Test
    @DisplayName("다른 캘린더로 옮길 때는 move 후 새 캘린더에서 내용을 맞추고, 항목의 캘린더를 바꾼다")
    void move() throws Exception {
        List<Long> ids = tripWithIds(items(item(null, 101L, 1, 1)));
        uploaded(ids.getFirst());
        upload("{\"calendarId\": \"%s\"}".formatted(FAMILY));
        expectRefresh();
        expectGoogle(HttpMethod.POST, event("primary", eventId(ids.getFirst())) + "/move?destination=" + FAMILY_ENCODED)
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.PUT, event(FAMILY_ENCODED, eventId(ids.getFirst())))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        worker.runOnce();

        google.verify();
        assertThat(jdbcTemplate.queryForObject("SELECT calendar_id FROM plan_items WHERE id = ?", String.class,
                ids.getFirst())).isEqualTo(FAMILY);
        assertThat(syncStatus(ids.getFirst())).isEqualTo("SYNCED");
    }

    @Test
    @DisplayName("429면 항목 시도 +1, 작업을 5초 뒤로 미루고, 시계가 지나면 이어서 성공한다")
    void backoffThenSuccess() throws Exception {
        List<Long> ids = tripAndUpload(items(item(null, 101L, 1, 1)), "{}");
        long jobId = latestJobId();
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withTooManyRequests());

        worker.runOnce();

        assertThat(job(jobId)).containsEntry("status", "PENDING")
                .containsEntry("next_attempt_at", LocalDateTime.of(2026, 11, 9, 10, 0, 5));
        assertThat(jobItems(jobId).getFirst()).containsEntry("attempt_count", 1).containsEntry("status", "PENDING");
        assertThat(worker.runOnce()).as("아직 차례가 아니다").isZero();

        google.verify();
        google.reset();
        clock.advance(Duration.ofSeconds(5));
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(worker.runOnce()).isEqualTo(1);

        google.verify();
        assertThat(job(jobId)).containsEntry("status", "DONE");
        assertThat(syncStatus(ids.getFirst())).isEqualTo("SYNCED");
    }

    @Test
    @DisplayName("5xx가 3번이면 그 항목은 GOOGLE_ERROR로 실패하고 작업은 FAILED다 (간격 5초 → 10초 → 20초)")
    void threeFailures() throws Exception {
        List<Long> ids = tripAndUpload(items(item(null, 101L, 1, 1)), "{}");
        long jobId = latestJobId();
        for (int attempt = 0; attempt < 3; attempt++) {
            google.reset();
            expectRefresh();
            expectGoogle(HttpMethod.POST, events("primary")).andRespond(withServerError());
            assertThat(worker.runOnce()).isEqualTo(1);
            google.verify();
            clock.advance(Duration.ofSeconds(5L << attempt));
        }
        assertThat(jobItems(jobId).getFirst()).containsEntry("status", "FAILED")
                .containsEntry("reason_code", "GOOGLE_ERROR").containsEntry("attempt_count", 3);

        google.reset();
        worker.runOnce();

        assertThat(job(jobId)).containsEntry("status", "FAILED").containsEntry("succeeded", 0);
        assertThat(syncStatus(ids.getFirst())).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("403은 NOT_WRITABLE로 그 항목만 실패(PARTIAL_FAILED). 403이라도 rateLimitExceeded면 다시 시도한다")
    void forbiddenAndRateLimit() throws Exception {
        List<Long> ids = tripAndUpload(items(item(null, 101L, 1, 1), item(null, 102L, 1, 2), item(null, 103L, 1, 3)),
                "{}");
        long jobId = latestJobId();
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\": {\"errors\": [{\"reason\": \"forbidden\"}]}}"));
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"errors\": [{\"reason\": \"rateLimitExceeded\"}]}}"));
        worker.runOnce();
        google.verify();
        assertThat(jobItems(jobId)).extracting(row -> row.get("status"), row -> row.get("reason_code"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("DONE", null),
                        org.assertj.core.groups.Tuple.tuple("FAILED", "NOT_WRITABLE"),
                        org.assertj.core.groups.Tuple.tuple("PENDING", null));

        google.reset();
        clock.advance(Duration.ofSeconds(5));
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        worker.runOnce();

        google.verify();
        assertThat(job(jobId)).containsEntry("status", "PARTIAL_FAILED").containsEntry("succeeded", 2);
        assertThat(syncStatus(ids.get(1))).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("토큰이 거부되면(invalid_grant) 연동을 REVOKED로 바꾸고 남은 항목을 AUTH_EXPIRED로 실패시킨다")
    void invalidGrant() throws Exception {
        tripAndUpload(items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)), "{}");
        long jobId = latestJobId();
        google.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"invalid_grant\"}"));

        worker.runOnce();

        assertThat(job(jobId)).containsEntry("status", "FAILED");
        assertThat(jobItems(jobId)).allSatisfy(row -> assertThat(row.get("reason_code")).isEqualTo("AUTH_EXPIRED"));
        assertThat(linkStatus(user.getId())).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("한 차례에 batch-size(50)개까지만 하고, 남으면 바로 다음 차례에 이어서 한다")
    void batches() throws Exception {
        tripAndUpload(items(item(null, 101L, 1, 1)), "{}");
        long jobId = latestJobId();
        // 작업 항목을 51개로 늘린다 (같은 항목을 가리키는 CREATE — 두 번째부터는 이미 올라가서 UPDATE가 된다)
        long planItemId = (Long) jobItems(jobId).getFirst().get("plan_item_id");
        for (int i = 0; i < 50; i++) {
            jdbcTemplate.update("INSERT INTO calendar_sync_job_items (job_id, plan_item_id, trip_id, operation, event_id, "
                    + "calendar_id, status, attempt_count) VALUES (?, ?, ?, 'CREATE', ?, 'primary', 'PENDING', 0)",
                    jobId, planItemId, trip[0], eventId(planItemId));
        }
        expectRefresh();
        expectGoogle(HttpMethod.POST, events("primary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 49; i++) {
            expectGoogle(HttpMethod.PUT, event("primary", eventId(planItemId)))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        worker.runOnce();
        google.verify();
        assertThat(job(jobId)).containsEntry("status", "PENDING");

        google.reset();
        expectRefresh();
        expectGoogle(HttpMethod.PUT, event("primary", eventId(planItemId)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        worker.runOnce();
        google.verify();
        assertThat(job(jobId)).containsEntry("status", "DONE");
    }

    @Test
    @DisplayName("지울 일정이 이미 없으면(404·410) 성공으로 본다")
    void deleteNotFound() throws Exception {
        List<Long> ids = tripWithIds(items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        ids.forEach(this::uploaded);
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/trips/{id}", trip[0]), token)).andExpect(status().isNoContent());
        expectRefresh();
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(0)))).andRespond(withResourceNotFound());
        expectGoogle(HttpMethod.DELETE, event("primary", eventId(ids.get(1)))).andRespond(withStatus(HttpStatus.GONE));

        worker.runOnce();

        google.verify();
        assertThat(job(latestJobId())).containsEntry("job_type", "DELETE_EVENTS").containsEntry("status", "DONE")
                .containsEntry("succeeded", 2);
    }

    @Test
    @DisplayName("작업을 넣은 뒤 연동을 끊었으면 남은 항목은 AUTH_EXPIRED로 실패한다 (구글을 부르지 않는다)")
    void linkRemoved() throws Exception {
        tripAndUpload(items(item(null, 101L, 1, 1)), "{}");
        linkRepository.deleteAll();

        worker.runOnce();

        google.verify();
        assertThat(job(latestJobId())).containsEntry("status", "FAILED");
        assertThat(jobItems(latestJobId()).getFirst()).containsEntry("reason_code", "AUTH_EXPIRED");
    }

    private List<Long> tripWithIds(String itemsJson) throws Exception {
        trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1), itemsJson);
        return itemIds(trip[1]);
    }

    private List<Long> tripAndUpload(String itemsJson, String uploadBody) throws Exception {
        List<Long> ids = tripWithIds(itemsJson);
        upload(uploadBody);
        return ids;
    }

    private void upload(String body) throws Exception {
        mockMvc.perform(json(post("/api/trips/{id}/calendar-uploads", trip[0]), token, body))
                .andExpect(status().isAccepted());
    }
}
