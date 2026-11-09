package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripCopyApiTest extends TripApiTestSupport {

    @Test
    @DisplayName("복제하면 \"{이름} 복사본\"으로 일정안·장소를 모두 복사하고 같은 일정안을 확정하며, 캘린더·방문 확인 상태는 비운다")
    void copy() throws Exception {
        String token = token(saveUser("copy@example.com"));
        String title = "가".repeat(28);
        long[] ids = createTrip(token, title, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        load(place(101L, "경복궁", 37.5796, 126.9770));
        savePlan(token, ids[0], ids[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        markSynced(itemIds(ids[1]).getFirst(), "evt1");
        long secondPlan = longAt(mockMvc.perform(auth(post("/api/trips/{id}/plans", ids[0]), token)).andReturn(), "$.id");
        jdbcTemplate.update("UPDATE trips SET visit_check_status = 'DONE', calendar_id = 'primary' WHERE id = ?", ids[0]);

        MvcResult copied = mockMvc.perform(auth(post("/api/trips/{id}/copy", ids[0]), token))
                .andExpect(status().isCreated()).andReturn();
        long copyId = longAt(copied, "$.id");

        mockMvc.perform(auth(get("/api/trips/{id}", copyId), token))
                .andExpect(jsonPath("$.title").value("가".repeat(26) + " 복사본"))
                .andExpect(jsonPath("$.plans.length()").value(2))
                .andExpect(jsonPath("$.plans[0].isConfirmed").value(true))
                .andExpect(jsonPath("$.plans[0].placeCount").value(1))
                .andExpect(jsonPath("$.calendar.calendarId").doesNotExist())
                .andExpect(jsonPath("$.calendar.sync").value("none"));
        Long copiedConfirmed = jdbcTemplate.queryForObject("SELECT confirmed_plan_id FROM trips WHERE id = ?",
                Long.class, copyId);
        assertThat(copiedConfirmed).isNotIn(ids[1], secondPlan);
        List<Long> copiedItems = itemIds(copiedConfirmed);
        assertThat(copiedItems).hasSize(1);
        assertThat(syncStatus(copiedItems.getFirst())).isEqualTo("NONE");
        assertThat(jdbcTemplate.queryForObject("SELECT visit_check_status FROM trips WHERE id = ?", String.class, copyId))
                .isNull();
    }
}
