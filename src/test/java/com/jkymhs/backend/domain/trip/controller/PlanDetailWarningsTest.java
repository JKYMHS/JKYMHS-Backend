package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 1일차 = 2026-11-10(화).
 */
class PlanDetailWarningsTest extends TripApiTestSupport {

    @Test
    @DisplayName("운영 시간·쉬는 날·축제 기간·식사 시간으로 항목별 경고를 계산하고, 그날 경고 수를 센다")
    void warnings() throws Exception {
        String token = token(saveUser("warn@example.com"));
        long[] ids = createTrip(token, "경고 여행", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910),
                restaurant(301L, "토속촌", 37.5779, 126.9714),
                festival(201L, "단풍 축제", 37.57, 126.98, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 30)));
        hours(101L, "09:00~18:00 (입장마감 17:00)", "매주 화요일");
        hours(102L, "09:00~18:00", "매주 월요일");

        mockMvc.perform(json(put("/api/trips/{t}/plans/{p}", ids[0], ids[1]), token, """
                        {"version": 0, "name": "기본 코스", "items": [
                          {"contentId": 102, "dayNumber": 1, "sortOrder": 1, "startTime": "12:00", "stayMinutes": 60},
                          {"contentId": 301, "dayNumber": 1, "sortOrder": 2, "startTime": "12:00", "endTime": "13:00"},
                          {"contentId": 101, "dayNumber": 1, "sortOrder": 3, "startTime": "17:30", "endTime": "19:00"},
                          {"contentId": 201, "dayNumber": 1, "sortOrder": 4},
                          {"contentId": 102, "dayNumber": 2, "sortOrder": 1, "startTime": "10:00", "endTime": "11:00"}
                        ]}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/trips/{t}/plans/{p}", ids[0], ids[1]), token))
                .andExpect(jsonPath("$.days[0].items[0].warnings", containsInAnyOrder("LUNCH_OVERLAP")))
                .andExpect(jsonPath("$.days[0].items[1].warnings", empty()))
                .andExpect(jsonPath("$.days[0].items[2].warnings",
                        containsInAnyOrder("DINNER_OVERLAP", "AFTER_LAST_ENTRY", "AFTER_CLOSE", "REST_DAY")))
                .andExpect(jsonPath("$.days[0].items[3].warnings", containsInAnyOrder("OUT_OF_FESTIVAL")))
                .andExpect(jsonPath("$.days[0].warningCount").value(6))
                .andExpect(jsonPath("$.days[1].items[0].warnings", empty()))
                .andExpect(jsonPath("$.days[1].warningCount").value(0));
    }

    @Test
    @DisplayName("TourAPI에서 내려간 장소는 contentDeleted=true이고 저장해 둔 이름으로 보인다")
    void deletedContent() throws Exception {
        String token = token(saveUser("deleted@example.com"));
        long[] ids = createTrip(token, "여행", TODAY, TODAY);
        load(place(101L, "경복궁", 37.5796, 126.9770));
        savePlan(token, ids[0], ids[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        contentService.markDeleted(java.util.List.of(101L));

        mockMvc.perform(auth(get("/api/trips/{t}/plans/{p}", ids[0], ids[1]), token))
                .andExpect(jsonPath("$.days[0].items[0].name").value("경복궁"))
                .andExpect(jsonPath("$.days[0].items[0].contentDeleted").value(true))
                .andExpect(jsonPath("$.days[0].items[0].imageUrl").value("https://img.test/101.jpg"));
        // 지워진 장소라도 그대로 두고 다시 저장할 수 있다 (새로 넣는 것만 막는다)
        long itemId = itemIds(ids[1]).getFirst();
        savePlan(token, ids[0], ids[1], 1, items(item(itemId, 101L, 1, 1))).andExpect(status().isOk());
        savePlan(token, ids[0], ids[1], 2, items(item(null, 101L, 1, 1))).andExpect(status().isNotFound());
    }
}
