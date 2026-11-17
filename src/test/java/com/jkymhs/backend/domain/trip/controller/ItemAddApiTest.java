package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanChangedEvent;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemAddApiTest extends TripApiTestSupport {

    private String token;
    private long tripId;
    private long planId;

    @BeforeEach
    void setUp() throws Exception {
        token = token(saveUser("add@example.com"));
        long[] ids = createTrip(token, "담기 여행", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21));
        tripId = ids[0];
        planId = ids[1];
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910),
                festival(201L, "단풍 축제", 37.57, 126.98, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 30)));
    }

    @Test
    @DisplayName("그날 마지막에 붙이고, 앞 장소에서 오는 이동을 채우고, 버전을 올리고, 행동 신호·확정안 변경 이벤트를 낸다")
    void append() throws Exception {
        add(101L, 1, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.tripTitle").value("담기 여행"))
                .andExpect(jsonPath("$.planName").value("기본 코스"));

        add(102L, 1, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.festivalOutOfRange").value(false));

        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/trips/{t}/plans/{p}", tripId, planId), token))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.days[0].items[1].move.mode").value("taxi"))
                .andExpect(jsonPath("$.days[0].items[1].move.isEstimated").value(true));
        assertThat(eventsOf(BehaviorSignalEvent.class)).hasSize(2);
        assertThat(eventsOf(ConfirmedPlanChangedEvent.class)).hasSize(2);
    }

    @Test
    @DisplayName("같은 날 같은 장소는 409, allowDuplicate=true면 넣는다")
    void duplicate() throws Exception {
        add(101L, 1, null).andExpect(status().isCreated());
        add(101L, 1, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ITEM_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("이미 같은 날에 추가된 장소입니다."));
        add(101L, 2, null).andExpect(status().isCreated());
        add(101L, 1, true).andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(2));
    }

    @Test
    @DisplayName("축제 기간 밖의 날에 축제를 담으면 festivalOutOfRange=true")
    void festivalOutOfRange() throws Exception {
        add(201L, 1, null).andExpect(jsonPath("$.festivalOutOfRange").value(true));
        add(201L, 2, null).andExpect(jsonPath("$.festivalOutOfRange").value(false));
    }

    @Test
    @DisplayName("지난 여행은 422 SCHEDULE_ALREADY_ENDED, 여행 기간 밖의 날은 400")
    void endedAndDayRange() throws Exception {
        long[] past = createTrip(token, "지난 여행", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        mockMvc.perform(json(post("/api/trips/{t}/plans/{p}/items", past[0], past[1]), token, """
                        {"contentId": 101, "dayNumber": 1}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ALREADY_ENDED"))
                .andExpect(jsonPath("$.message").value("지난 일정에는 장소를 추가할 수 없습니다."));

        add(101L, 3, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("여행 기간 안의 날짜를 선택해주세요."));
        add(999L, 1, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("일정안에 장소가 30개면 400 SCHEDULE_LIMIT_EXCEEDED")
    void limit() throws Exception {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            items.add(item(null, 101L, 1 + (i % 2), i));
        }
        savePlan(token, tripId, planId, 0, String.join(",", items)).andExpect(status().isOk());

        add(102L, 1, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("일정에는 장소를 최대 30개까지 넣을 수 있습니다."));
    }

    private ResultActions add(long contentId, int day, Boolean allowDuplicate) throws Exception {
        return mockMvc.perform(json(post("/api/trips/{t}/plans/{p}/items", tripId, planId), token, """
                {"contentId": %d, "dayNumber": %d, "allowDuplicate": %s}
                """.formatted(contentId, day, allowDuplicate)));
    }
}
