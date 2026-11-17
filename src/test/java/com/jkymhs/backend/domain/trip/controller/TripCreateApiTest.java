package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripCreateApiTest extends TripApiTestSupport {

    private String token;

    @BeforeEach
    void setUp() {
        User user = saveUser("trip@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("여행을 만들면 201과 함께 빈 \"기본 코스\" 일정안이 확정 상태로 생기고, 지역·출발지가 저장된다")
    void createWithDefaultConfirmedPlan() throws Exception {
        long[] ids = createTrip(token, """
                {"title": "  가을 서울  ", "startDate": "2026-11-20", "endDate": "2026-11-22",
                 "regionIds": [%d, %d, %d], "companion": "kids", "childAgeGroup": "ELEMENTARY",
                 "origin": {"name": "서울역", "address": "서울 용산구 한강대로 405", "lat": 37.5547, "lng": 126.9707}}
                """.formatted(jongnoId, busanId, jongnoId), TODAY.plusDays(11));

        mockMvc.perform(auth(get("/api/trips/{id}", ids[0]), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("가을 서울"))
                .andExpect(jsonPath("$.startDate").value("2026-11-20"))
                .andExpect(jsonPath("$.companion").value("kids"))
                .andExpect(jsonPath("$.childAgeGroup").value("ELEMENTARY"))
                .andExpect(jsonPath("$.regions[*].fullName", contains("서울 종로구", "부산")))
                .andExpect(jsonPath("$.origin.name").value("서울역"))
                .andExpect(jsonPath("$.origin.lat").value(37.5547))
                .andExpect(jsonPath("$.confirmedPlanId").value(ids[1]))
                .andExpect(jsonPath("$.calendar.sync").value("none"))
                .andExpect(jsonPath("$.plans.length()").value(1))
                .andExpect(jsonPath("$.plans[0].name").value("기본 코스"))
                .andExpect(jsonPath("$.plans[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.plans[0].isConfirmed").value(true))
                .andExpect(jsonPath("$.plans[0].placeCount").value(0));
    }

    @Test
    @DisplayName("오늘 출발하는 14일 여행은 만들 수 있고, 15일이면 400 SCHEDULE_LIMIT_EXCEEDED")
    void maxFourteenDays() throws Exception {
        create(TODAY, TODAY.plusDays(13), "friends", "").andExpect(status().isCreated());

        create(TODAY, TODAY.plusDays(14), "friends", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("여행 기간은 최대 14일까지 선택할 수 있습니다."));
    }

    @Test
    @DisplayName("가는 날이 지났으면 400 \"지난 날짜는 선택할 수 없습니다.\"")
    void pastStart() throws Exception {
        create(TODAY.minusDays(1), TODAY.plusDays(1), "friends", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("지난 날짜는 선택할 수 없습니다."));
    }

    @Test
    @DisplayName("오는 날이 가는 날보다 앞이면 400")
    void endBeforeStart() throws Exception {
        create(TODAY.plusDays(3), TODAY.plusDays(2), "friends", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("오는 날은 가는 날 이후로 선택해주세요."));
    }

    @Test
    @DisplayName("아이와 가는데 아이 나이가 없으면 400")
    void kidsWithoutAge() throws Exception {
        create(TODAY, TODAY.plusDays(1), "kids", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("아이 나이를 선택해주세요."));
    }

    @Test
    @DisplayName("없는 지역이 있으면 400 \"잘못된 필터 값입니다.\", 지역이 6개면 400")
    void regions() throws Exception {
        mockMvc.perform(json(post("/api/trips"), token, """
                        {"title": "여행", "startDate": "%s", "endDate": "%s", "regionIds": [%d, 999999], "companion": "alone"}
                        """.formatted(TODAY, TODAY, seoulId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("잘못된 필터 값입니다."));

        mockMvc.perform(json(post("/api/trips"), token, """
                        {"title": "여행", "startDate": "%s", "endDate": "%s", "regionIds": [1, 2, 3, 4, 5, 6], "companion": "alone"}
                        """.formatted(TODAY, TODAY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("지역은 최대 5개까지 선택할 수 있습니다."));
    }

    @Test
    @DisplayName("제목이 31자거나 동행 값이 틀리면 400 INVALID_INPUT")
    void fieldValidation() throws Exception {
        mockMvc.perform(json(post("/api/trips"), token, """
                        {"title": "%s", "startDate": "%s", "endDate": "%s", "regionIds": [%d], "companion": "alone"}
                        """.formatted("가".repeat(31), TODAY, TODAY, seoulId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("여행 이름은 30자까지 입력할 수 있습니다."));

        create(TODAY, TODAY, "dog", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void unauthorized() throws Exception {
        mockMvc.perform(post("/api/trips").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions create(java.time.LocalDate start, java.time.LocalDate end, String companion, String extra)
            throws Exception {
        return mockMvc.perform(json(post("/api/trips"), token, """
                {"title": "여행", "startDate": "%s", "endDate": "%s", "regionIds": [%d], "companion": "%s"%s}
                """.formatted(start, end, seoulId, companion, extra)));
    }
}
