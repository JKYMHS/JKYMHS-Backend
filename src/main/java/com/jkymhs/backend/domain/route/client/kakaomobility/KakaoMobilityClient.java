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
