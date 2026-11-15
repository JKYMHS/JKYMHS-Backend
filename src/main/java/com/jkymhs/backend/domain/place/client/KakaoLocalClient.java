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

/**
 * 카카오 로컬 API (키워드 검색, 주소 검색, 좌표 → 주소).
 * 출발지(역·공항·집 주소)는 TourAPI 데이터에 없어서 카카오에서 찾는다. 키를 FE에 두지 않으려고 서버가 대신 부른다.
 *
 * 응답은 {@code {"documents": [...], "meta": {...}}} 형식이다. 좌표는 x = 경도, y = 위도 문자열로 온다.
 * RestClient.Builder를 생성자로 받는 이유는 TourApiClient와 같다 (테스트에서 MockRestServiceServer를 붙인다).
 */
public class KakaoLocalClient {

    private final RestClient restClient;
    private final KakaoLocalProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public KakaoLocalClient(RestClient.Builder restClientBuilder, KakaoLocalProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /** 장소 이름 검색 ("용산역"). 정확도순 */
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

    /** 주소 검색 ("한강대로 405"). 주소 자체가 결과라 이름도 주소로 채운다 */
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

    /** 좌표가 가리키는 주소. 바다·해외처럼 주소가 없으면 빈 값 */
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
            // 예외 메시지에는 요청 주소(좌표 포함)가 들어 있어서 종류만 남긴다
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
