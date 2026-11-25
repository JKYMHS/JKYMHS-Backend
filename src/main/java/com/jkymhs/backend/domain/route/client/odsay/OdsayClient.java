package com.jkymhs.backend.domain.route.client.odsay;

import com.jkymhs.backend.domain.route.service.LatLng;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class OdsayClient {
    private static final int SUBWAY = 1;
    private static final int BUS = 2;

    private final RestClient restClient;
    private final OdsayProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OdsayClient(RestClient.Builder restClientBuilder, OdsayProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public Optional<TransitRoute> search(LatLng from, LatLng to) {
        if (!properties.hasApiKey()) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/searchPubTransPathT")
                .queryParam("SX", "{sx}")
                .queryParam("SY", "{sy}")
                .queryParam("EX", "{ex}")
                .queryParam("EY", "{ey}")
                .queryParam("apiKey", "{apiKey}")
                .encode()
                .buildAndExpand(Map.of("sx", from.lng(), "sy", from.lat(), "ex", to.lng(), "ey", to.lat(),
                        "apiKey", properties.apiKey()))
                .toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parse(body);
        } catch (RestClientException | JacksonException e) {
            log.warn("ODsay 대중교통 길찾기 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TransitRoute> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = jsonMapper.readTree(body);
        if (root.has("error")) {
            log.info("ODsay 경로 없음: {}", root.path("error"));
            return Optional.empty();
        }
        JsonNode path = root.path("result").path("path").path(0);
        if (path.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode info = path.path("info");
        int minutes = info.path("totalTime").asInt(0);
        if (minutes <= 0) {
            return Optional.empty();
        }
        return Optional.of(new TransitRoute(minutes, info.path("totalDistance").asInt(0), summary(path.path("subPath"))));
    }

    private static String summary(JsonNode subPaths) {
        List<String> legs = new ArrayList<>();
        for (JsonNode subPath : subPaths) {
            int type = subPath.path("trafficType").asInt();
            JsonNode lane = subPath.path("lane").path(0);
            if (type == SUBWAY) {
                legs.add("지하철 " + lane.path("name").asString("").replace("수도권 ", "").trim());
            } else if (type == BUS) {
                legs.add("버스 " + lane.path("busNo").asString("").trim());
            }
        }
        if (legs.isEmpty()) {
            return null;
        }
        String joined = String.join(" → ", legs);
        int transfers = legs.size() - 1;
        return transfers > 0 ? joined + " · 환승 " + transfers + "회" : joined;
    }
}
