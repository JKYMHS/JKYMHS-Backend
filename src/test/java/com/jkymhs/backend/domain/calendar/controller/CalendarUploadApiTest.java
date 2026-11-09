package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarUploadApiTest extends CalendarApiTestSupport {

    private User user;
    private String token;
    private long[] trip;
    private List<Long> ids;

    @BeforeEach
    void setUp() throws Exception {
        loadPlaces();
        user = saveUser("upload@example.com");
        token = token(user);
        trip = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1),
                items(item(null, 101L, 1, 1), item(null, 102L, 1, 2), item(null, 103L, 1, 3)));
        ids = itemIds(trip[1]);
    }

    @Test
    @DisplayName("항목을 고르지 않으면 동기화 안 된 항목만 기본 캘린더로 올리는 작업을 넣는다 (202, 구글은 아직 안 부른다)")
    void defaultItems() throws Exception {
        link(user);
        uploaded(ids.get(0));

        upload("{}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.pollAfterMs").value(2000));

        long jobId = latestJobId();
        assertThat(job(jobId)).containsEntry("job_type", "UPLOAD").containsEntry("status", "PENDING")
                .containsEntry("trip_id", trip[0]).containsEntry("calendar_id", "primary").containsEntry("total", 2);
        assertThat(jobItems(jobId)).extracting(row -> row.get("plan_item_id"), row -> row.get("operation"),
                        row -> row.get("event_id"), row -> row.get("start_label"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ids.get(1), "CREATE", eventId(ids.get(1)), "11.10 종일"),
                        org.assertj.core.groups.Tuple.tuple(ids.get(2), "CREATE", eventId(ids.get(2)), "11.10 종일"));
        assertThat(syncStatus(ids.get(0))).isEqualTo("SYNCED");
        assertThat(syncStatus(ids.get(1))).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT calendar_id FROM trips WHERE id = ?", String.class, trip[0]))
                .isEqualTo("primary");
    }

    @Test
    @DisplayName("다른 캘린더를 고르면 고른 항목은 새로 올리고, 이미 올린 나머지 일정은 같은 작업에서 새 캘린더로 옮긴다")
    void moveOnCalendarChange() throws Exception {
        link(user);
        uploaded(ids.get(0));
        uploaded(ids.get(1));

        upload("""
                {"calendarId": "family@group.calendar.google.com", "itemIds": [%d]}""".formatted(ids.get(2)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemCount").value(3));

        List<Map<String, Object>> items = jobItems(latestJobId());
        assertThat(items).extracting(row -> row.get("plan_item_id"), row -> row.get("operation"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ids.get(2), "CREATE"),
                        org.assertj.core.groups.Tuple.tuple(ids.get(0), "MOVE"),
                        org.assertj.core.groups.Tuple.tuple(ids.get(1), "MOVE"));
        assertThat(items).allSatisfy(row -> assertThat(row.get("calendar_id")).isEqualTo("family@group.calendar.google.com"));
    }

    @Test
    @DisplayName("진행 중인 업로드가 있으면 409 CALENDAR_SYNC_IN_PROGRESS이고 description에 그 작업 id가 있다")
    void inProgress() throws Exception {
        link(user);
        upload("{}").andExpect(status().isAccepted());
        long running = latestJobId();

        upload("{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALENDAR_SYNC_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("이미 동기화가 진행 중입니다."))
                .andExpect(jsonPath("$.description").value(String.valueOf(running)));
        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연동이 없거나 만료면 403 CALENDAR_NOT_LINKED, 확정안이 없으면 400, 확정안에 없는 항목이면 400 INVALID_INPUT")
    void errors() throws Exception {
        upload("{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_LINKED"));
        link(user);
        jdbcTemplate.update("UPDATE google_calendar_links SET status = 'REVOKED'");
        upload("{}").andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE google_calendar_links SET status = 'ACTIVE'");

        upload("{\"itemIds\": [999999]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("확정 일정안에 없는 항목이 포함되어 있습니다."));
        upload("{\"itemIds\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올릴 일정을 하나 이상 골라주세요."));

        jdbcTemplate.update("UPDATE trips SET confirmed_plan_id = NULL");
        upload("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_CONFIRMED"));
        assertThat(jobCount()).isZero();
    }

    private ResultActions upload(String body) throws Exception {
        return mockMvc.perform(json(post("/api/trips/{id}/calendar-uploads", trip[0]), token, body));
    }
}
