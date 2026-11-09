package com.jkymhs.backend.domain.recommendation;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.recommendation.entity.FailureReason;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationConditions;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import com.jkymhs.backend.domain.recommendation.service.RecommendationWorker;
import com.jkymhs.backend.domain.recommendation.service.TravelStyleMappingSeeder;
import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.global.model.Companion;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추천 API 테스트 공통 준비물. 시계는 2026-11-09(월) 10:00 KST, AI는 mock, 경로 API 키가 비어 있어서
 * 1km 넘는 구간은 직선거리 어림값(isEstimated=true)이다. 비동기 실행기가 동기라서 POST가 끝나면 작업도 끝나 있다.
 *
 * 기본 장소: 서울 종로구 궁 10곳(OUTDOOR, 서로 수백 m 간격)과 박물관 4곳(INDOOR), 음식점 2곳. 출발지는 서울역.
 */
public abstract class RecommendationTestSupport extends TripApiTestSupport {

    protected static final double ORIGIN_LAT = 37.5547;
    protected static final double ORIGIN_LNG = 126.9707;

    @Autowired
    protected TravelStyleMappingSeeder travelStyleMappingSeeder;

    @Autowired
    protected RecommendationRequestRepository requestRepository;

    @Autowired
    protected RecommendationWorker worker;

    @BeforeEach
    void seedStyleMappings() {
        travelStyleMappingSeeder.seed();
    }

    /** 궁 10곳(1001~1010)·박물관 4곳(2001~2004)·음식점 2곳(3001~3002). 인기 점수는 id가 작을수록 높다 */
    protected void loadSeoulPlaces() {
        for (int i = 0; i < 10; i++) {
            load(place(1001 + i, "궁" + (i + 1), 37.5700 + i * 0.002, 126.9770 + i * 0.001));
        }
        for (int i = 0; i < 4; i++) {
            load(museum(2001 + i, "박물관" + (i + 1), 37.5750 + i * 0.002, 126.9800 + i * 0.001));
        }
        load(restaurant(3001, "식당1", 37.5705, 126.9772), restaurant(3002, "식당2", 37.5760, 126.9801));
        for (int i = 0; i < 10; i++) {
            popularity(1001 + i, 900 - i * 50);
        }
        for (int i = 0; i < 4; i++) {
            popularity(2001 + i, 300 - i * 50);
        }
    }

    protected static ContentSource museum(long id, String name, double lat, double lng) {
        return new ContentSource(id, ContentType.ATTRACTION, 14, name, "주소 " + name, BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lng), "11", "110", "VE", "VE07", "VE070100", "https://img.test/" + id + ".jpg",
                "02-000-0000", null, null, LocalDateTime.of(2026, 11, 1, 9, 0));
    }

    protected void popularity(long contentId, int score) {
        jdbcTemplate.update("UPDATE contents SET popularity_score = ? WHERE id = ?", score, contentId);
    }

    // ---------- 요청 ----------

    /** 서울 전체, 오늘~내일(1박 2일), 친구, 10:00 출발 */
    protected String body() {
        return body(TODAY, TODAY.plusDays(1), "friends", "");
    }

    /**
     * @param extra 끝에 덧붙일 필드 (예: {@code , "requiredContentIds": [1001]})
     */
    protected String body(LocalDate start, LocalDate end, String companion, String extra) {
        return """
                {"regionId": %d, "startDate": "%s", "endDate": "%s", "startTime": "10:00",
                 "origin": {"name": "서울역", "address": "서울 용산구 한강대로 405", "lat": %s, "lng": %s},
                 "companion": "%s"%s}
                """.formatted(seoulId, start, end, ORIGIN_LAT, ORIGIN_LNG, companion, extra);
    }

    protected ResultActions request(String token, String body) throws Exception {
        return mockMvc.perform(json(post("/api/recommendations"), token, body));
    }

    /** 추천을 요청하고(작업까지 끝난다) requestId를 돌려준다 */
    protected long recommend(String token, String body) throws Exception {
        MvcResult result = request(token, body).andExpect(status().isAccepted()).andReturn();
        return longAt(result, "$.requestId");
    }

    protected MvcResult result(String token, long requestId) throws Exception {
        return mockMvc.perform(auth(get("/api/recommendations/{id}", requestId), token))
                .andExpect(status().isOk()).andReturn();
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    // ---------- 행 직접 넣기 (G16c: PENDING 같은 중간 상태는 POST로 만들 수 없다) ----------

    protected RecommendationRequest insertRequest(long userId, RecommendationStatus status, LocalDateTime requestedAt) {
        RecommendationConditions conditions = new RecommendationConditions(seoulId, TODAY, TODAY.plusDays(1),
                LocalTime.of(10, 0), "서울역", "서울 용산구 한강대로 405", ORIGIN_LAT, ORIGIN_LNG, Companion.FRIENDS, null,
                false, null);
        RecommendationRequest request = requestRepository.save(RecommendationRequest.create(userId, conditions,
                PlanSource.AI, null, List.of(), "key-" + System.nanoTime(), requestedAt, Duration.ofHours(24)));
        if (status != RecommendationStatus.PENDING) {
            requestRepository.finish(request.getId(), status,
                    status == RecommendationStatus.FAILED ? FailureReason.INTERNAL : null);
        }
        return request;
    }

    protected String statusOf(long requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM recommendation_requests WHERE id = ?", String.class,
                requestId);
    }
}
