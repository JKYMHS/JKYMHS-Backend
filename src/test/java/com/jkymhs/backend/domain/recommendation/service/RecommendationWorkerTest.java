package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseClient;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseRequest;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.recommendation.client.ai.AiTimeoutException;
import com.jkymhs.backend.domain.recommendation.client.ai.MockAiCourseClient;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityClient;
import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityProperties;
import com.jkymhs.backend.domain.route.client.odsay.OdsayClient;
import com.jkymhs.backend.domain.route.client.odsay.OdsayProperties;
import com.jkymhs.backend.domain.route.service.RouteService;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 추천 작업을 직접 부른다. AI 응답은 worker의 aiCourseClient를 바꿔 끼워서 정한다 (끝나면 mock으로 되돌림).
 */
class RecommendationWorkerTest extends RecommendationTestSupport {

    @Autowired
    private RouteService routeService;

    private Object originalAi;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        originalAi = ReflectionTestUtils.getField(worker, "aiCourseClient");
        user = saveUser("worker@example.com");
        token = token(user);
    }

    @AfterEach
    void restore() {
        ReflectionTestUtils.setField(worker, "aiCourseClient", originalAi);
    }

    @Test
    @DisplayName("mock AI: 코스 3개, 이동 → 장소 순서, 점심 칸(11:30~12:30 시작)과 주변 음식점 수, 첫날 출발지는 요청 출발지")
    void doneWithMockAi() throws Exception {
        loadSeoulPlaces();
        long requestId = runFor(insertPending());

        assertThat(statusOf(requestId)).isEqualTo("DONE");
        MvcResult result = result(token, requestId);
        assertThat((List<?>) read(result, "$.courses")).hasSize(3);
        assertThat((String) read(result, "$.courses[0].title")).isEqualTo("인기 명소 코스");
        assertThat((String) read(result, "$.courses[0].days[0].origin.name")).isEqualTo("서울역");
        assertThat((String) read(result, "$.courses[0].days[0].date")).isEqualTo(TODAY.toString());

        List<String> types = read(result, "$.courses[0].days[0].items[*].type");
        assertThat(types.getFirst()).isEqualTo("move");
        assertThat(types.get(1)).isEqualTo("place");
        assertThat(types).contains("meal");
        // 하루 장소 수: 온보딩 전 회원은 "여유롭게" → 최대 3곳
        assertThat(types.stream().filter("place"::equals).count()).isEqualTo(3);

        List<String> mealStarts = read(result, "$.courses[0].days[0].items[?(@.type == 'meal')].startTime");
        assertThat(mealStarts).singleElement().satisfies(start -> assertThat(start).isBetween("11:30", "12:30"));
        List<String> mealTypes = read(result, "$.courses[0].days[0].items[?(@.type == 'meal')].mealType");
        assertThat(mealTypes).containsExactly("LUNCH");
        List<Integer> nearby = read(result, "$.courses[0].days[0].items[?(@.type == 'meal')].nearbyRestaurantCount");
        assertThat(nearby.getFirst()).isPositive();

        // 첫 장소는 1위 궁, 시작 10:00 + 서울역에서 오는 이동 (경로 키가 없어서 직선 어림값)
        assertThat((Integer) read(result, "$.courses[0].days[0].items[1].contentId")).isEqualTo(1001);
        assertThat((Boolean) read(result, "$.courses[0].days[0].items[0].isEstimated")).isTrue();
        assertThat((String) read(result, "$.courses[0].days[0].items[1].reason")).isEqualTo("궁 · 인기 장소");
        assertThat((String) read(result, "$.courses[0].days[0].items[1].io")).isEqualTo("OUTDOOR");
        // 둘째 날 출발점은 전날 마지막 장소
        String lastPlaceOfDay1 = read(result,
                "$.courses[0].days[0].items[?(@.type == 'place')].name").toString();
        assertThat(lastPlaceOfDay1).contains((String) read(result, "$.courses[0].days[1].origin.name"));
        assertThat((Integer) read(result, "$.courses[0].summary.places")).isEqualTo(6);
        assertThat((String) read(result, "$.courses[1].title")).isEqualTo("여유로운 동선 코스");
    }

    @Test
    @DisplayName("AI가 후보에 없는 id를 한 번 주면 위반 목록을 붙여 다시 요청하고, 두 번째가 맞으면 DONE이다")
    void retryOnceThenSuccess() throws Exception {
        loadSeoulPlaces();
        List<AiCourseRequest> calls = new ArrayList<>();
        MockAiCourseClient mock = new MockAiCourseClient();
        useAi(request -> {
            calls.add(request);
            return calls.size() == 1 ? withUnknownId(mock.compose(request)) : mock.compose(request);
        });

        long requestId = runFor(insertPending());

        assertThat(statusOf(requestId)).isEqualTo("DONE");
        assertThat(calls).hasSize(2);
        assertThat(calls.getFirst().violations()).isEmpty();
        assertThat(calls.get(1).violations()).anySatisfy(v -> assertThat(v).contains("후보에 없는 장소입니다: 999999"));
    }

    @Test
    @DisplayName("두 번 다 규칙을 어기면 기본 추천 코스(FALLBACK)로 끝난다")
    void invalidTwiceFallback() throws Exception {
        loadSeoulPlaces();
        AtomicInteger calls = new AtomicInteger();
        MockAiCourseClient mock = new MockAiCourseClient();
        useAi(request -> {
            calls.incrementAndGet();
            return withUnknownId(mock.compose(request));
        });

        long requestId = runFor(insertPending());

        assertThat(calls.get()).isEqualTo(2);
        assertThat(statusOf(requestId)).isEqualTo("FALLBACK");
        MvcResult result = result(token, requestId);
        assertThat((Boolean) read(result, "$.isFallback")).isTrue();
        assertThat((List<String>) read(result, "$.courses[*].title")).containsOnly(FallbackCourseBuilder.TITLE);
        assertThat((List<?>) read(result, "$.courses")).hasSize(3);
    }

    @Test
    @DisplayName("AI가 시간 초과면 다시 부르지 않고 기본 추천 코스로 끝난다 (실제로 기다리지 않는다)")
    void timeoutFallback() throws Exception {
        loadSeoulPlaces();
        AtomicInteger calls = new AtomicInteger();
        useAi(request -> {
            calls.incrementAndGet();
            throw new AiTimeoutException("timeout", null);
        });

        long requestId = runFor(insertPending());

        assertThat(calls.get()).isEqualTo(1);
        assertThat(statusOf(requestId)).isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("후보가 하루 2곳도 안 되면 FAILED(NOT_ENOUGH_CANDIDATES)이고 횟수에서 빠진다")
    void notEnoughCandidates() throws Exception {
        load(place(1001, "궁1", 37.57, 126.977), place(1002, "궁2", 37.572, 126.978), place(1003, "궁3", 37.574, 126.979));

        long requestId = runFor(insertPending());

        assertThat(statusOf(requestId)).isEqualTo("FAILED");
        MvcResult result = result(token, requestId);
        assertThat((String) read(result, "$.reason")).isEqualTo("NOT_ENOUGH_CANDIDATES");
        assertThat((Boolean) read(result, "$.refunded")).isTrue();
        MvcResult quota = mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/recommendations/quota"), token)).andReturn();
        assertThat((Integer) read(quota, "$.used")).isZero();
    }

    @Test
    @DisplayName("후보 풀은 지역·기간별로 Redis에 1시간 저장되고, 다음 작업은 DB 대신 그 목록을 쓴다")
    void candidatePoolCached() throws Exception {
        loadSeoulPlaces();
        runFor(insertPending());
        String key = RecommendationRedisKeys.candidates(seoulId, List.of(), TODAY, TODAY.plusDays(1));
        assertThat(redisTemplate.opsForValue().get(key)).startsWith("[1001,1002");
        assertThat(redisTemplate.getExpire(key)).isBetween(Duration.ofHours(1).minusMinutes(1).toSeconds(),
                Duration.ofHours(1).toSeconds());

        // 캐시를 궁 4곳으로 바꾸면 다음 작업의 코스에는 그 4곳만 나온다
        redisTemplate.opsForValue().set(key, "[1007,1008,1009,1010]");
        long requestId = runFor(insertPending());
        MvcResult result = result(token, requestId);
        List<Integer> placeIds = read(result, "$.courses[*].days[*].items[?(@.type == 'place')].contentId");
        assertThat(placeIds).isNotEmpty().allSatisfy(id -> assertThat(id).isBetween(1007, 1010));
    }

    @Test
    @DisplayName("구간이 1km를 넘으면 ODsay·카카오모빌리티를 불러 실제 경로(isEstimated=false)를 넣는다")
    void routesFromRouteService() throws Exception {
        loadSeoulPlaces();
        Object originalOdsay = ReflectionTestUtils.getField(routeService, "odsayClient");
        Object originalKakao = ReflectionTestUtils.getField(routeService, "kakaoMobilityClient");
        RestClient.Builder odsayBuilder = RestClient.builder();
        MockRestServiceServer odsay = MockRestServiceServer.bindTo(odsayBuilder).build();
        RestClient.Builder kakaoBuilder = RestClient.builder();
        MockRestServiceServer kakao = MockRestServiceServer.bindTo(kakaoBuilder).build();
        odsay.expect(ExpectedCount.manyTimes(), requestTo(startsWith("https://odsay.test/v1/api/searchPubTransPathT")))
                .andRespond(withSuccess("""
                        {"result": {"path": [{"pathType": 1, "info": {"totalTime": 12, "totalDistance": 2000},
                          "subPath": [{"trafficType": 1, "lane": [{"name": "수도권 1호선"}]}]}]}}
                        """, MediaType.APPLICATION_JSON));
        kakao.expect(ExpectedCount.manyTimes(), requestTo(startsWith("https://kakao-mobility.test/v1/directions")))
                .andRespond(withSuccess("""
                        {"routes": [{"result_code": 0, "summary": {"distance": 2100, "duration": 600},
                          "sections": [{"roads": [{"vertexes": [126.9707, 37.5547, 126.9770, 37.5700]}]}]}]}
                        """, MediaType.APPLICATION_JSON));
        ReflectionTestUtils.setField(routeService, "odsayClient", new OdsayClient(odsayBuilder,
                new OdsayProperties("https://odsay.test/v1/api", "key", Duration.ofSeconds(1))));
        ReflectionTestUtils.setField(routeService, "kakaoMobilityClient", new KakaoMobilityClient(kakaoBuilder,
                new KakaoMobilityProperties("https://kakao-mobility.test", "key", Duration.ofSeconds(1))));
        try {
            long requestId = runFor(insertPending());
            MvcResult result = result(token, requestId);
            // 서울역 → 첫 궁(약 1.7km): 대중교통 12분 ≤ 택시 10분 × 1.5 → 대중교통, 대안 택시
            assertThat((String) read(result, "$.courses[0].days[0].items[0].mode")).isEqualTo("transit");
            assertThat((Integer) read(result, "$.courses[0].days[0].items[0].minutes")).isEqualTo(12);
            assertThat((String) read(result, "$.courses[0].days[0].items[0].altMode")).isEqualTo("taxi");
            assertThat((Boolean) read(result, "$.courses[0].days[0].items[0].isEstimated")).isFalse();
            // 궁 사이(수백 m)는 외부 API 없이 도보
            List<String> modes = read(result, "$.courses[0].days[0].items[?(@.type == 'move')].mode");
            assertThat(modes.subList(1, modes.size())).containsOnly("walk");
        } finally {
            ReflectionTestUtils.setField(routeService, "odsayClient", originalOdsay);
            ReflectionTestUtils.setField(routeService, "kakaoMobilityClient", originalKakao);
        }
    }

    @Test
    @DisplayName("다른 작업이 이미 끝낸 요청(PENDING 아님)은 다시 처리하지 않는다")
    void skipNonPending() {
        loadSeoulPlaces();
        RecommendationRequest done = insertRequest(user.getId(), RecommendationStatus.FAILED, LocalDateTime.now(clock));
        worker.run(done.getId());
        assertThat(statusOf(done.getId())).isEqualTo("FAILED");
        assertThat(redisTemplate.hasKey(RecommendationRedisKeys.result(done.getId()))).isFalse();
    }

    // ---------- 도움 ----------

    private RecommendationRequest insertPending() {
        return insertRequest(user.getId(), RecommendationStatus.PENDING, LocalDateTime.now(clock));
    }

    private long runFor(RecommendationRequest request) {
        worker.run(request.getId());
        return request.getId();
    }

    private void useAi(AiCourseClient client) {
        ReflectionTestUtils.setField(worker, "aiCourseClient", client);
    }

    // 첫 코스 첫날 첫 장소를 후보에 없는 id로 바꾼다
    private static AiCourseResponse withUnknownId(AiCourseResponse response) {
        List<AiCourseResponse.Course> courses = new ArrayList<>(response.courses());
        AiCourseResponse.Course first = courses.getFirst();
        List<AiCourseResponse.Day> days = new ArrayList<>(first.days());
        List<AiCourseResponse.Item> items = new ArrayList<>(days.getFirst().items());
        items.set(0, new AiCourseResponse.Item(999999L, 60, "없는 장소"));
        days.set(0, new AiCourseResponse.Day(1, items));
        courses.set(0, new AiCourseResponse.Course(first.title(), first.summary(), days));
        return new AiCourseResponse(courses);
    }
}
