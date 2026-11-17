package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.event.CalendarEventRef;
import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanSwitchedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanApiTest extends TripApiTestSupport {

    private String token;
    private long tripId;
    private long basePlan;

    @BeforeEach
    void setUp() throws Exception {
        token = token(saveUser("plan@example.com"));
        long[] ids = createTrip(token, "일정안 여행", TODAY.plusDays(1), TODAY.plusDays(2));
        tripId = ids[0];
        basePlan = ids[1];
        load(place(101L, "경복궁", 37.5796, 126.9770));
    }

    @Test
    @DisplayName("이름 없이 만들면 \"직접 만든 일정안 {n}\"이고, 10개가 넘으면 400 SCHEDULE_LIMIT_EXCEEDED")
    void createAndLimit() throws Exception {
        long created = longAt(mockMvc.perform(auth(post("/api/trips/{id}/plans", tripId), token))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        mockMvc.perform(json(post("/api/trips/{id}/plans", tripId), token, """
                {"name": "비 오는 날 코스"}
                """)).andExpect(status().isCreated());
        for (int i = 0; i < 7; i++) {
            mockMvc.perform(auth(post("/api/trips/{id}/plans", tripId), token)).andExpect(status().isCreated());
        }

        mockMvc.perform(auth(post("/api/trips/{id}/plans", tripId), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("일정안은 최대 10개까지 만들 수 있습니다."));
        mockMvc.perform(auth(get("/api/trips/{id}", tripId), token))
                .andExpect(jsonPath("$.plans.length()").value(10))
                .andExpect(jsonPath("$.plans[1].id").value(created))
                .andExpect(jsonPath("$.plans[1].name").value("직접 만든 일정안 2"))
                .andExpect(jsonPath("$.plans[1].isConfirmed").value(false))
                .andExpect(jsonPath("$.plans[2].name").value("비 오는 날 코스"));
    }

    @Test
    @DisplayName("복제하면 \"{이름} 복사본\"으로 장소를 복사하고 캘린더 상태는 비우며, 이름을 바꿀 수 있다")
    void copyAndRename() throws Exception {
        savePlan(token, tripId, basePlan, 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        markSynced(itemIds(basePlan).getFirst(), "evt1");

        long copy = longAt(mockMvc.perform(auth(post("/api/trips/{id}/plans/{planId}/copy", tripId, basePlan), token))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        mockMvc.perform(auth(get("/api/trips/{id}/plans/{planId}", tripId, copy), token))
                .andExpect(jsonPath("$.name").value("기본 코스 복사본"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.isConfirmed").value(false))
                .andExpect(jsonPath("$.days[0].items[0].name").value("경복궁"))
                .andExpect(jsonPath("$.days[0].items[0].calendarState").value("new"));

        mockMvc.perform(json(patch("/api/trips/{id}/plans/{planId}", tripId, copy), token, """
                        {"name": "  새 이름 "}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(copy))
                .andExpect(jsonPath("$.name").value("새 이름"));
        mockMvc.perform(json(patch("/api/trips/{id}/plans/{planId}", tripId, copy), token, """
                        {"name": " "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("일정안 이름을 입력해주세요."));
    }

    @Test
    @DisplayName("마지막 일정안은 지울 수 없다 (409 LAST_PLAN_CANNOT_DELETE)")
    void lastPlan() throws Exception {
        mockMvc.perform(auth(delete("/api/trips/{id}/plans/{planId}", tripId, basePlan), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_PLAN_CANNOT_DELETE"))
                .andExpect(jsonPath("$.message").value("마지막 일정안은 삭제할 수 없습니다."));
    }

    @Test
    @DisplayName("확정안을 지우면 확정이 풀리고, 캘린더에 올린 일정은 정리 이벤트로 알린다")
    void deleteConfirmed() throws Exception {
        mockMvc.perform(auth(post("/api/trips/{id}/plans", tripId), token)).andExpect(status().isCreated());
        savePlan(token, tripId, basePlan, 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        markSynced(itemIds(basePlan).getFirst(), "evt1");

        mockMvc.perform(auth(delete("/api/trips/{id}/plans/{planId}", tripId, basePlan), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/trips/{id}", tripId), token))
                .andExpect(jsonPath("$.confirmedPlanId").doesNotExist())
                .andExpect(jsonPath("$.plans.length()").value(1));
        assertThat(itemIds(basePlan)).isEmpty();
        assertThat(eventsOf(CalendarEventsOrphanedEvent.class)).singleElement()
                .satisfies(e -> assertThat(e.events()).containsExactly(new CalendarEventRef("primary", "evt1")));
    }

    @Test
    @DisplayName("확정안을 바꾸면 예전 확정안의 캘린더 일정을 담아 ConfirmedPlanSwitchedEvent를 발행한다. 여행에 없는 일정안은 404")
    void confirmSwitch() throws Exception {
        savePlan(token, tripId, basePlan, 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        markSynced(itemIds(basePlan).getFirst(), "evt1");
        long other = longAt(mockMvc.perform(auth(post("/api/trips/{id}/plans", tripId), token)).andReturn(), "$.id");

        mockMvc.perform(json(put("/api/trips/{id}/confirmed-plan", tripId), token, """
                        {"planId": %d}
                        """.formatted(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedPlanId").value(other));

        List<ConfirmedPlanSwitchedEvent> switched = eventsOf(ConfirmedPlanSwitchedEvent.class);
        assertThat(switched).hasSize(1);
        assertThat(switched.getFirst().oldPlanId()).isEqualTo(basePlan);
        assertThat(switched.getFirst().newPlanId()).isEqualTo(other);
        assertThat(switched.getFirst().oldEvents()).containsExactly(new CalendarEventRef("primary", "evt1"));

        // 같은 일정안을 다시 확정하면 아무 일도 없다
        mockMvc.perform(json(put("/api/trips/{id}/confirmed-plan", tripId), token, """
                {"planId": %d}
                """.formatted(other))).andExpect(status().isOk());
        assertThat(eventsOf(ConfirmedPlanSwitchedEvent.class)).hasSize(1);

        long[] otherTrip = createTrip(token, "다른 여행", TODAY, TODAY);
        mockMvc.perform(json(put("/api/trips/{id}/confirmed-plan", tripId), token, """
                        {"planId": %d}
                        """.formatted(otherTrip[1])))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
