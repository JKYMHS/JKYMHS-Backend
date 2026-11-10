package com.jkymhs.backend.domain.route;

import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityClient;
import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityProperties;
import com.jkymhs.backend.domain.route.client.odsay.OdsayClient;
import com.jkymhs.backend.domain.route.client.odsay.OdsayProperties;
import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.PolylineEncoder;
import com.jkymhs.backend.domain.route.service.RouteRedisKeys;
import com.jkymhs.backend.domain.route.service.RouteService;
import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 ODsay·카카오모빌리티 대신 MockRestServiceServer가 응답한다. RouteService 빈의 클라이언트를 테스트용으로 바꿔 끼우고 끝나면 되돌린다.
 */
class RouteApiTest extends TripApiTestSupport {

    private static final String ODSAY_URL = "https://odsay.test/v1/api";
    private static final String KAKAO_URL = "https://kakao-mobility.test";

    // 경복궁 → 서울역 (직선 약 3km)
    private static final LatLng FROM = new LatLng(37.5796, 126.9770);
    private static final LatLng TO = new LatLng(37.5547, 126.9707);

    @Autowired
    private RouteService routeService;

    private MockRestServiceServer odsay;
    private MockRestServiceServer kakao;
    private Object originalOdsay;
    private Object originalKakao;
    private String token;

    @BeforeEach
    void bindMocks() {
        originalOdsay = ReflectionTestUtils.getField(routeService, "odsayClient");
        originalKakao = ReflectionTestUtils.getField(routeService, "kakaoMobilityClient");
        RestClient.Builder odsayBuilder = RestClient.builder();
        odsay = MockRestServiceServer.bindTo(odsayBuilder).build();
        RestClient.Builder kakaoBuilder = RestClient.builder();
        kakao = MockRestServiceServer.bindTo(kakaoBuilder).build();
        ReflectionTestUtils.setField(routeService, "odsayClient",
                new OdsayClient(odsayBuilder, new OdsayProperties(ODSAY_URL, "odsay+key/1", Duration.ofSeconds(1))));
        ReflectionTestUtils.setField(routeService, "kakaoMobilityClient",
                new KakaoMobilityClient(kakaoBuilder, new KakaoMobilityProperties(KAKAO_URL, "kakao-key", Duration.ofSeconds(1))));
        token = token(saveUser("route@example.com"));
    }

    @AfterEach
    void restoreClients() {
        ReflectionTestUtils.setField(routeService, "odsayClient", originalOdsay);
        ReflectionTestUtils.setField(routeService, "kakaoMobilityClient", originalKakao);
    }

    @Test
    @DisplayName("직선 1km 이하는 외부 API 없이 도보 (분당 67m)")
    void walk() throws Exception {
        calculate(segment(new LatLng(37.5796, 126.9770), new LatLng(37.5751, 126.9768)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].mode").value("walk"))
                .andExpect(jsonPath("$.routes[0].minutes").value(8))
                .andExpect(jsonPath("$.routes[0].altMode").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].isEstimated").value(false))
                .andExpect(jsonPath("$.routes[0].distanceMeters").value(501));
        odsay.verify();
        kakao.verify();
    }

    @Test
    @DisplayName("대중교통이 택시의 1.5배 이하면 대중교통(대안 택시)을 추천하고 7일 캐시에 넣는다")
    void transitWithinRatio() throws Exception {
        expectOdsay(odsayPath(30, 3500));
        expectKakao(kakaoRoute(1500, 3200));

        calculate(segment(FROM, TO), "friends")
                .andExpect(jsonPath("$.routes[0].mode").value("transit"))
                .andExpect(jsonPath("$.routes[0].minutes").value(30))
                .andExpect(jsonPath("$.routes[0].altMode").value("taxi"))
                .andExpect(jsonPath("$.routes[0].altMinutes").value(25))
                .andExpect(jsonPath("$.routes[0].summary").value("지하철 1호선 → 버스 472 · 환승 1회"))
                .andExpect(jsonPath("$.routes[0].polyline").value(PolylineEncoder.straight(FROM, TO)))
                .andExpect(jsonPath("$.routes[0].distanceMeters").value(3500));
        odsay.verify();
        kakao.verify();

        String key = RouteRedisKeys.route(FROM, TO);
        assertThat(redisTemplate.opsForHash().get(key, "mode")).isEqualTo("TRANSIT");
        assertThat(redisTemplate.getExpire(key)).isBetween(Duration.ofDays(7).minusMinutes(1).toSeconds(),
                Duration.ofDays(7).toSeconds());
    }

    @Test
    @DisplayName("대중교통이 택시의 1.5배보다 오래 걸리면 택시(대안 대중교통)이고 polyline은 실제 도로 경로다")
    void taxiBeyondRatio() throws Exception {
        expectOdsay(odsayPath(60, 3500));
        expectKakao(kakaoRoute(1200, 3200));

        calculate(segment(FROM, TO), null)
                .andExpect(jsonPath("$.routes[0].mode").value("taxi"))
                .andExpect(jsonPath("$.routes[0].minutes").value(20))
                .andExpect(jsonPath("$.routes[0].altMode").value("transit"))
                .andExpect(jsonPath("$.routes[0].altMinutes").value(60))
                .andExpect(jsonPath("$.routes[0].summary").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].polyline").value(PolylineEncoder.encode(List.of(
                        new LatLng(37.5796, 126.9770), new LatLng(37.5700, 126.9750), new LatLng(37.5547, 126.9707)))))
                .andExpect(jsonPath("$.routes[0].distanceMeters").value(3200));
    }

    @Test
    @DisplayName("아이와 가면 도보 기준이 500m라서, 800m 구간은 외부 API로 계산한다")
    void kidsThreshold() throws Exception {
        LatLng near = new LatLng(37.5724, 126.9770);
        calculate(segment(FROM, near), "friends").andExpect(jsonPath("$.routes[0].mode").value("walk"));

        expectOdsay("{\"error\": {\"code\": \"-98\", \"msg\": \"출, 도착지가 700m이내입니다.\"}}");
        expectKakao(kakaoRoute(300, 900));
        calculate(segment(FROM, near), "kids")
                .andExpect(jsonPath("$.routes[0].mode").value("taxi"))
                .andExpect(jsonPath("$.routes[0].minutes").value(5));
    }

    @Test
    @DisplayName("ODsay가 경로를 못 찾으면 택시만 (대안 없음)")
    void odsayFails() throws Exception {
        expectOdsay("{\"error\": [{\"code\": \"500\", \"message\": \"server error\"}]}");
        expectKakao(kakaoRoute(900, 3200));

        calculate(segment(FROM, TO), null)
                .andExpect(jsonPath("$.routes[0].mode").value("taxi"))
                .andExpect(jsonPath("$.routes[0].altMode").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].altMinutes").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].isEstimated").value(false));
    }

    @Test
    @DisplayName("둘 다 실패하면 직선거리 어림값(isEstimated, 자동차 30km/h)이고 캐시하지 않는다")
    void bothFail() throws Exception {
        odsay.expect(requestTo(startsWith(ODSAY_URL))).andRespond(withServerError());
        kakao.expect(requestTo(startsWith(KAKAO_URL))).andRespond(withServerError());

        calculate(segment(FROM, TO), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].mode").value("taxi"))
                .andExpect(jsonPath("$.routes[0].isEstimated").value(true))
                .andExpect(jsonPath("$.routes[0].minutes").value(6));
        assertThat(redisTemplate.hasKey(RouteRedisKeys.route(FROM, TO))).isFalse();
    }

    @Test
    @DisplayName("캐시에 있으면 외부 API를 부르지 않는다")
    void cacheHit() throws Exception {
        redisTemplate.opsForHash().putAll(RouteRedisKeys.route(FROM, TO), Map.of("mode", "TRANSIT", "minutes", "33",
                "altMode", "", "altMinutes", "", "summary", "버스 7022", "polyline", "xyz", "distance", "3400"));

        calculate(segment(FROM, TO), null)
                .andExpect(jsonPath("$.routes[0].mode").value("transit"))
                .andExpect(jsonPath("$.routes[0].minutes").value(33))
                .andExpect(jsonPath("$.routes[0].altMode").value(nullValue()))
                .andExpect(jsonPath("$.routes[0].summary").value("버스 7022"));
        odsay.verify();
        kakao.verify();
    }

    @Test
    @DisplayName("구간이 31개면 400, 0개도 400")
    void segmentLimit() throws Exception {
        String many = IntStream.range(0, 31).mapToObj(i -> segment(FROM, FROM)).collect(Collectors.joining(","));
        calculate(many, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("구간은 최대 30개까지 계산할 수 있습니다."));
        calculate("", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private void expectOdsay(String body) {
        odsay.expect(requestTo(startsWith(ODSAY_URL + "/searchPubTransPathT?")))
                .andExpect(requestTo(containsString("apiKey=odsay%2Bkey%2F1")))
                .andExpect(queryParam("SX", "126.977"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectKakao(String body) {
        kakao.expect(requestTo(startsWith(KAKAO_URL + "/v1/directions?")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK kakao-key"))
                .andExpect(queryParam("priority", "RECOMMEND"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String odsayPath(int minutes, int distance) {
        return """
                {"result": {"path": [{"pathType": 3, "info": {"totalTime": %d, "totalDistance": %d},
                  "subPath": [{"trafficType": 3}, {"trafficType": 1, "lane": [{"name": "수도권 1호선"}]},
                              {"trafficType": 2, "lane": [{"busNo": "472"}]}, {"trafficType": 3}]}]}}
                """.formatted(minutes, distance);
    }

    private static String kakaoRoute(int durationSeconds, int distance) {
        return """
                {"routes": [{"result_code": 0, "summary": {"distance": %d, "duration": %d},
                  "sections": [{"roads": [{"vertexes": [126.9770, 37.5796, 126.9750, 37.5700]},
                                          {"vertexes": [126.9707, 37.5547]}]}]}]}
                """.formatted(distance, durationSeconds);
    }

    private static String segment(LatLng from, LatLng to) {
        return """
                {"from": {"lat": %s, "lng": %s}, "to": {"lat": %s, "lng": %s}}""".formatted(from.lat(), from.lng(), to.lat(), to.lng());
    }

    private ResultActions calculate(String segments, String companion) throws Exception {
        String companionJson = companion == null ? "" : ", \"companion\": \"%s\"".formatted(companion);
        return mockMvc.perform(json(post("/api/routes"), token, """
                {"segments": [%s]%s}
                """.formatted(segments, companionJson)));
    }
}
