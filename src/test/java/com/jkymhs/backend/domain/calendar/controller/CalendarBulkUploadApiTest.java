package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarBulkUploadApiTest extends CalendarApiTestSupport {

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        loadPlaces();
        user = saveUser("bulk@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("여러 여행(지난 여행 포함)을 한 작업으로 올리고, 확정 전·이미 동기화된 여행은 이유와 함께 건너뛴다")
    void bulk() throws Exception {
        link(user);
        long[] past = tripWith(token, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1),
                items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)));
        long[] unconfirmed = createTrip(token, "확정 전", TODAY.plusDays(3), TODAY.plusDays(3));
        jdbcTemplate.update("UPDATE trips SET confirmed_plan_id = NULL WHERE id = ?", unconfirmed[0]);
        long[] synced = tripWith(token, TODAY.plusDays(5), TODAY.plusDays(5), items(item(null, 103L, 1, 1)));
        uploaded(itemIds(synced[1]).getFirst());

        bulk("""
                {"type": "BULK_UPLOAD", "tripIds": [%d, %d, %d]}""".formatted(past[0], unconfirmed[0], synced[0]))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.trips[0].tripId").value(past[0]))
                .andExpect(jsonPath("$.trips[0].itemCount").value(2))
                .andExpect(jsonPath("$.trips[0].skipped").value(nullValue()))
                .andExpect(jsonPath("$.trips[1].skipped").value("NOT_CONFIRMED"))
                .andExpect(jsonPath("$.trips[2].skipped").value("ALREADY_SYNCED"))
                .andExpect(jsonPath("$.trips[2].itemCount").value(0));

        long jobId = latestJobId();
        assertThat(job(jobId)).containsEntry("job_type", "BULK_UPLOAD").containsEntry("trip_id", null)
                .containsEntry("total", 2).containsEntry("status", "PENDING");
        assertThat(jobItems(jobId)).allSatisfy(row -> assertThat(row.get("trip_id")).isEqualTo(past[0]));
    }

    @Test
    @DisplayName("11개 이상이면 400 SCHEDULE_LIMIT_EXCEEDED \"한 번에 최대 10개 여행까지 올릴 수 있습니다.\"")
    void tooMany() throws Exception {
        link(user);
        String ids = LongStream.rangeClosed(1, 11).mapToObj(String::valueOf).collect(Collectors.joining(","));

        bulk("{\"type\": \"BULK_UPLOAD\", \"tripIds\": [" + ids + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("한 번에 최대 10개 여행까지 올릴 수 있습니다."));
    }

    @Test
    @DisplayName("다른 회원의 여행이 섞이면 400 INVALID_INPUT, 연동이 없으면 403, 진행 중인 업로드가 있으면 409")
    void errors() throws Exception {
        long[] mine = tripWith(token, TODAY.plusDays(1), TODAY.plusDays(1), items(item(null, 101L, 1, 1)));
        long[] others = createTrip(token(saveUser("other@example.com")), "남의 여행", TODAY.plusDays(1), TODAY.plusDays(1));
        String body = "{\"type\": \"BULK_UPLOAD\", \"tripIds\": [%d]}";

        bulk(body.formatted(mine[0])).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_LINKED"));
        link(user);
        bulk("{\"type\": \"BULK_UPLOAD\", \"tripIds\": [%d, %d]}".formatted(mine[0], others[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("올릴 수 없는 여행이 포함되어 있습니다."));
        bulk("{\"type\": \"SYNC\", \"tripIds\": [%d]}".formatted(mine[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 작업 종류입니다."));

        bulk(body.formatted(mine[0])).andExpect(status().isAccepted());
        bulk(body.formatted(mine[0])).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALENDAR_SYNC_IN_PROGRESS"));
    }

    private ResultActions bulk(String body) throws Exception {
        return mockMvc.perform(json(post("/api/calendar/jobs"), token, body));
    }
}
