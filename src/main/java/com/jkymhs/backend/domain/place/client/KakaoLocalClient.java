package com.jkymhs.backend.domain.place.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KakaoLocalClient {

    private final RestClient restClient;
    private final KakaoLocalProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public KakaoLocalClient(RestClient.Builder restClientBuilder, KakaoLocalProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public List<KakaoPlace> searchKeyword(String query, int size) {
        JsonNode root = get(uri("/v2/local/search/keyword.json").queryParam("query", query).queryParam("size", size));
        List<KakaoPlace> places = new ArrayList<>();
        for (JsonNode doc : root.path("documents")) {
            Double lat = number(doc.path("y"));
            Double lng = number(doc.path("x"));
            if (lat == null || lng == null) {
                continue;
            }
            places.add(new KakaoPlace(text(doc.path("id")), text(doc.path("place_name")), text(doc.path("address_name")),
                    text(doc.path("road_address_name")), text(doc.path("category_group_name")), lat, lng));
        }
        return places;
    }

    public List<KakaoPlace> searchAddress(String query) {
        JsonNode root = get(uri("/v2/local/search/address.json").queryParam("query", query));
        List<KakaoPlace> places = new ArrayList<>();
        for (JsonNode doc : root.path("documents")) {
            Double lat = number(doc.path("y"));
            Double lng = number(doc.path("x"));
            String name = text(doc.path("address_name"));
            if (lat == null || lng == null || name == null) {
                continue;
            }
            String jibun = text(doc.path("address").path("address_name"));
            String road = text(doc.path("road_address").path("address_name"));
            places.add(new KakaoPlace(null, name, jibun != null ? jibun : name, road, null, lat, lng));
        }
        return places;
    }

    public Optional<KakaoAddress> coordToAddress(double lat, double lng) {
        JsonNode root = get(uri("/v2/local/geo/coord2address.json").queryParam("x", lng).queryParam("y", lat));
        for (JsonNode doc : root.path("documents")) {
            String address = text(doc.path("address").path("address_name"));
            String road = text(doc.path("road_address").path("address_name"));
            if (address == null && road == null) {
                continue;
            }
            return Optional.of(new KakaoAddress(address != null ? address : road, road,
                    text(doc.path("road_address").path("building_name"))));
        }
        return Optional.empty();
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl()).path(path);
    }

    private JsonNode get(UriComponentsBuilder uriBuilder) {
        if (!properties.hasRestApiKey()) {
            throw new KakaoLocalException("카카오 REST API 키가 설정되지 않음");
        }
        URI uri = uriBuilder.encode().build().toUri();
        String body;
        try {
            body = restClient.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new KakaoLocalException("카카오 로컬 응답 오류: " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new KakaoLocalException("카카오 로컬 호출 실패: " + e.getClass().getSimpleName());
        }
        if (body == null || body.isBlank()) {
            throw new KakaoLocalException("카카오 로컬 빈 응답");
        }
        try {
            JsonNode root = jsonMapper.readTree(body);
            if (!root.path("documents").isArray()) {
                throw new KakaoLocalException("카카오 로컬 응답에 documents 없음");
            }
            return root;
        } catch (JacksonException e) {
            throw new KakaoLocalException("카카오 로컬 응답을 읽을 수 없음");
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString("").trim();
        return value.isEmpty() ? null : value;
    }

    private static Double number(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
