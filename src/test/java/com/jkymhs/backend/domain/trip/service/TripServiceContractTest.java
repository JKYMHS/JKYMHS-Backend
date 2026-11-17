package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.route.service.MoveMode;
import com.jkymhs.backend.domain.route.service.RouteResult;
import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanSwitchedEvent;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.Companion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * AI 추천 도메인이 쓰는 여행 서비스 계약 (createTripWithPlan, addPlan, getOwnedTrip).
 */
class TripServiceContractTest extends TripApiTestSupport {

    @Autowired
    private TripService tripService;

    private static final RouteResult TAXI = new RouteResult(MoveMode.TAXI, 15, MoveMode.TRANSIT, 25, null, "poly", false, 4000);

    @Test
    @DisplayName("createTripWithPlan은 지난 날짜도 받고, 초안 일정안을 확정안으로 넣으며 넘긴 이동은 그대로, 없는 이동은 채운다")
    void createTripWithPlan() throws Exception {
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910));
        var user = saveUser("ai@example.com");
        PlanDraft draft = new PlanDraft("AI 추천 코스", PlanSource.AI, 77L, List.of(
                new PlanItemDraft(102L, 1, 2, LocalTime.of(13, 0), LocalTime.of(14, 0), 60, "산책하기 좋아요", false, TAXI),
                new PlanItemDraft(101L, 1, 1, LocalTime.of(10, 0), LocalTime.of(12, 0), 120, "꼭 가볼 곳", true, null)));

        TripPlanIds ids = tripService.createTripWithPlan(user.getId(), new NewTripCommand("AI 여행",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), List.of(seoulId), Companion.COUPLE, null, null), draft);

        mockMvc.perform(auth(get("/api/trips/{t}/plans/{p}", ids.tripId(), ids.planId()), token(user)))
                .andExpect(jsonPath("$.isConfirmed").value(true))
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.name").value("AI 추천 코스"))
                .andExpect(jsonPath("$.totalMoveMinutes").value(15))
                .andExpect(jsonPath("$.days[0].items[0].name").value("경복궁"))
                .andExpect(jsonPath("$.days[0].items[0].isRequired").value(true))
                .andExpect(jsonPath("$.days[0].items[0].aiReason").value("꼭 가볼 곳"))
                .andExpect(jsonPath("$.days[0].items[0].startTime").value("10:00"))
                .andExpect(jsonPath("$.days[0].items[1].move.mode").value("taxi"))
                .andExpect(jsonPath("$.days[0].items[1].move.altMinutes").value(25));
        assertThat(eventsOf(BehaviorSignalEvent.class)).hasSize(2);
        assertThat(tripService.getOwnedTrip(user.getId(), ids.tripId()))
                .isEqualTo(new TripRef(ids.tripId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), 2));
    }

    @Test
    @DisplayName("addPlan은 확정 여부를 고를 수 있고, 초안이 여행보다 길면 422, 다른 회원의 여행이면 403")
    void addPlan() throws Exception {
        load(place(101L, "경복궁", 37.5796, 126.9770));
        var user = saveUser("ai@example.com");
        long[] trip = createTrip(token(user), "여행", TODAY, TODAY.plusDays(1));
        PlanDraft draft = new PlanDraft("다시 추천", PlanSource.AI_RETRY, 78L,
                List.of(new PlanItemDraft(101L, 2, 1, null, null, null, null, false, null)));

        TripPlanIds added = tripService.addPlan(user.getId(), trip[0], draft, false);
        assertThat(jdbcTemplate.queryForObject("SELECT confirmed_plan_id FROM trips WHERE id = ?", Long.class, trip[0]))
                .isEqualTo(trip[1]);

        TripPlanIds confirmed = tripService.addPlan(user.getId(), trip[0], draft, true);
        assertThat(jdbcTemplate.queryForObject("SELECT confirmed_plan_id FROM trips WHERE id = ?", Long.class, trip[0]))
                .isEqualTo(confirmed.planId());
        assertThat(eventsOf(ConfirmedPlanSwitchedEvent.class)).singleElement()
                .satisfies(e -> assertThat(e.oldPlanId()).isEqualTo(trip[1]));
        assertThat(added.planId()).isNotEqualTo(confirmed.planId());

        PlanDraft tooLong = new PlanDraft("긴 코스", PlanSource.AI, 79L,
                List.of(new PlanItemDraft(101L, 3, 1, null, null, null, null, false, null)));
        assertThatThrownBy(() -> tripService.addPlan(user.getId(), trip[0], tooLong, false))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_EXCEEDS_TRIP_PERIOD));
        var stranger = saveUser("stranger@example.com");
        assertThatThrownBy(() -> tripService.addPlan(stranger.getId(), trip[0], draft, false))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
