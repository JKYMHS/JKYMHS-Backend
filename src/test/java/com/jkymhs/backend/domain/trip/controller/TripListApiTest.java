package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오늘 = 2026-11-09. 다가오는 여행(종료일 ≥ 오늘)은 시작일 순, 그 뒤 지난 여행은 시작일 역순.
 */
class TripListApiTest extends TripApiTestSupport {

    private String token;
    private long ongoing;   // 11-08 ~ 11-10 (진행 중 → 다가오는 쪽)
    private long soon;      // 11-12 ~ 11-13
    private long later;     // 11-20 ~ 11-22
    private long recent;    // 11-01 ~ 11-03 (지난)
    private long old;       // 10-01 ~ 10-03 (지난)

    @BeforeEach
    void setUp() throws Exception {
        User user = saveUser("list@example.com", ConsentType.BEHAVIOR_DATA);
        token = token(user);
        later = createTrip(token, "나중", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22))[0];
        old = createTrip(token, "옛날", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))[0];
        soon = createTrip(token, "곧", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13))[0];
        recent = createTrip(token, "최근", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))[0];
        ongoing = createTrip(token, "진행 중", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10))[0];
        // 다른 회원의 여행은 보이지 않는다
        createTrip(token(saveUser("other@example.com")), "남의 여행", TODAY, TODAY);
    }

    @Test
    @DisplayName("전체: 다가오는 여행을 시작일 순으로, 이어서 지난 여행을 시작일 역순으로 보여주고 첫 페이지에 개수를 준다")
    void allOrderingAndCounts() throws Exception {
        mockMvc.perform(auth(get("/api/trips"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) ongoing, (int) soon, (int) later, (int) recent, (int) old)))
                .andExpect(jsonPath("$.items[*].group", contains("upcoming", "upcoming", "upcoming", "past", "past")))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.counts.all").value(5))
                .andExpect(jsonPath("$.counts.upcoming").value(3))
                .andExpect(jsonPath("$.counts.past").value(2))
                .andExpect(jsonPath("$.counts.unconfirmed").value(0))
                .andExpect(jsonPath("$.items[0].regions[0].name").value("서울"))
                .andExpect(jsonPath("$.items[0].companion").value("friends"))
                .andExpect(jsonPath("$.items[0].planCount").value(1))
                .andExpect(jsonPath("$.items[0].confirmedPlan.name").value("기본 코스"))
                .andExpect(jsonPath("$.items[0].confirmedPlan.placeCount").value(0))
                .andExpect(jsonPath("$.items[0].visit").value(nullValue()))
                .andExpect(jsonPath("$.items[0].sync").value("none"));
    }

    @Test
    @DisplayName("size=2로 넘기면 다가오는 → 지난 여행 경계를 넘어 이어지고, 두 번째 페이지부터는 개수·배너가 null이다")
    void cursorAcrossGroups() throws Exception {
        MvcResult first = mockMvc.perform(auth(get("/api/trips").param("size", "2"), token))
                .andExpect(jsonPath("$.items[*].id", contains((int) ongoing, (int) soon)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn();
        String cursor = com.jayway.jsonpath.JsonPath.read(body(first), "$.nextCursor");

        MvcResult second = mockMvc.perform(auth(get("/api/trips").param("size", "2").param("cursor", cursor), token))
                .andExpect(jsonPath("$.items[*].id", contains((int) later, (int) recent)))
                .andExpect(jsonPath("$.counts").value(nullValue()))
                .andExpect(jsonPath("$.visitCheckPending").value(nullValue()))
                .andReturn();
        String cursor2 = com.jayway.jsonpath.JsonPath.read(body(second), "$.nextCursor");

        mockMvc.perform(auth(get("/api/trips").param("size", "2").param("cursor", cursor2), token))
                .andExpect(jsonPath("$.items[*].id", contains((int) old)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("upcoming·past·unconfirmed 필터")
    void filters() throws Exception {
        mockMvc.perform(auth(get("/api/trips").param("filter", "upcoming"), token))
                .andExpect(jsonPath("$.items[*].id", contains((int) ongoing, (int) soon, (int) later)));
        mockMvc.perform(auth(get("/api/trips").param("filter", "past"), token))
                .andExpect(jsonPath("$.items[*].id", contains((int) recent, (int) old)));

        // 확정 일정안이 없는 여행: 일정안을 하나 더 만들고 확정안을 지운다
        long planId = jdbcTemplate.queryForObject("SELECT confirmed_plan_id FROM trips WHERE id = ?", Long.class, soon);
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/trips/{id}/plans", soon), token)).andExpect(status().isCreated());
        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/trips/{id}/plans/{planId}", soon, planId), token)).andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/trips").param("filter", "unconfirmed"), token))
                .andExpect(jsonPath("$.items[*].id", contains((int) soon)))
                .andExpect(jsonPath("$.items[0].confirmedPlan").value(nullValue()))
                .andExpect(jsonPath("$.counts.unconfirmed").value(1));
    }

    @Test
    @DisplayName("다른 필터에서 만든 커서나 깨진 커서는 400 INVALID_CURSOR, size 51은 400")
    void invalidCursor() throws Exception {
        MvcResult first = mockMvc.perform(auth(get("/api/trips").param("size", "1"), token)).andReturn();
        String cursor = com.jayway.jsonpath.JsonPath.read(body(first), "$.nextCursor");

        mockMvc.perform(auth(get("/api/trips").param("filter", "past").param("cursor", cursor), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andExpect(jsonPath("$.message").value("목록을 처음부터 다시 불러옵니다."));
        mockMvc.perform(auth(get("/api/trips").param("cursor", "not-a-cursor"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mockMvc.perform(auth(get("/api/trips").param("size", "51"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("끝난 확정 여행은 방문 확인 n/m, 캘린더 상태, 첫 장소 사진을 보여주고 배너에 뜬다")
    void visitSyncThumbnail() throws Exception {
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910));
        long planId = jdbcTemplate.queryForObject("SELECT confirmed_plan_id FROM trips WHERE id = ?", Long.class, recent);
        savePlan(token, recent, planId, 0, items(item(null, 101L, 1, 1), item(null, 102L, 1, 2)))
                .andExpect(status().isOk());
        List<Long> ids = itemIds(planId);
        markSynced(ids.get(0), "evt1");
        markSynced(ids.get(1), "evt2");
        mockMvc.perform(json(put("/api/trips/{id}/visit-check", recent), token, """
                {"items": [{"itemId": %d, "visited": true, "rating": 5}]}
                """.formatted(ids.get(0)))).andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/trips").param("filter", "past"), token))
                .andExpect(jsonPath("$.items[0].id").value(recent))
                .andExpect(jsonPath("$.items[0].visit.checked").value(1))
                .andExpect(jsonPath("$.items[0].visit.total").value(2))
                .andExpect(jsonPath("$.items[0].sync").value("synced"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://img.test/101.jpg"))
                .andExpect(jsonPath("$.items[0].confirmedPlan.placeCount").value(2))
                .andExpect(jsonPath("$.visitCheckPending.tripId").value(recent))
                .andExpect(jsonPath("$.visitCheckPending.endDate").value("2026-11-03"));

        jdbcTemplate.update("UPDATE plan_items SET sync_status = 'DIRTY' WHERE id = ?", ids.get(1));
        mockMvc.perform(auth(get("/api/trips").param("filter", "past"), token))
                .andExpect(jsonPath("$.items[0].sync").value("changed"));
    }
}
