package com.jkymhs.backend.domain.route.client.kakaomobility;

import com.jkymhs.backend.domain.route.service.LatLng;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 카카오모빌리티 자동차 길찾기 ({@code GET /v1/directions}). 택시 시간·거리·도로 경로를 얻는다.
 *
 * 실패(키 없음, 4xx/5xx, 타임아웃, 길 없음 result_code≠0)는 예외 대신 빈 Optional로 돌려준다.
 * 경로 계산은 대중교통·택시 중 하나만 있어도 답할 수 있고, 둘 다 없으면 직선거리로 어림잡기 때문이다 (사용자 화면이 502로 막히지 않게).
 *
 * 좌표 순서는 "경도,위도"(x,y)다. 응답의 vertexes도 [x1, y1, x2, y2, …] 순서다.
 * RestClient.Builder를 생성자로 받는 이유는 TourApiClient와 같다 (테스트에서 MockRestServiceServer를 붙인다).
 */
@Slf4j
public class KakaoMobilityClient {

    private final RestClient restClient;
    private final KakaoMobilityProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public KakaoMobilityClient(RestClient.Builder restClientBuilder, KakaoMobilityProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public Optional<CarRoute> directions(LatLng from, LatLng to) {
        if (!properties.hasApiKey()) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/v1/directions")
                .queryParam("origin", point(from))
                .queryParam("destination", point(to))
                .queryParam("priority", "RECOMMEND")
                .encode()
                .build()
                .toUri();
        try {
            String body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RestClientException | JacksonException e) {
            log.warn("카카오모빌리티 길찾기 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CarRoute> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        JsonNode route = jsonMapper.readTree(body).path("routes").path(0);
        // 0 = 성공. 104(출발지와 도착지가 너무 가까움) 등은 길이 없다고 본다
        if (route.isMissingNode() || route.path("result_code").asInt(-1) != 0) {
            return Optional.empty();
        }
        JsonNode summary = route.path("summary");
        List<LatLng> path = new ArrayList<>();
        for (JsonNode section : route.path("sections")) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertexes = road.path("vertexes");
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    path.add(new LatLng(vertexes.get(i + 1).asDouble(), vertexes.get(i).asDouble()));
                }
            }
        }
        return Optional.of(new CarRoute(summary.path("duration").asInt(), summary.path("distance").asInt(), path));
    }

    private static String point(LatLng latLng) {
        return String.format(Locale.ROOT, "%.7f,%.7f", latLng.lng(), latLng.lat());
    }
}
