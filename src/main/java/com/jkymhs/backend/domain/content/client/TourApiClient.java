package com.jkymhs.backend.domain.content.client;

import com.jkymhs.backend.domain.content.service.ContentRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TourApiClient {

    public static final int PAGE_SIZE = 1000;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CALL_COUNTER_TTL = Duration.ofDays(2);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String OK = "0000";

    private final RestClient restClient;
    private final TourApiProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public TourApiClient(RestClient.Builder restClientBuilder, TourApiProperties properties,
                         StringRedisTemplate redisTemplate, Clock clock) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return properties.hasServiceKey();
    }

    public TourApiPage ldongCodes(int pageNo) {
        return call("ldongCode2", page(pageNo, "lDongListYn", "Y"));
    }

    public TourApiPage lclsSystmCodes(int pageNo) {
        return call("lclsSystmCode2", page(pageNo, "lclsSystmListYn", "Y"));
    }

    public TourApiPage areaBasedSyncList(int contentTypeId, LocalDate modifiedSince, int pageNo) {
        Map<String, String> params = page(pageNo, "contentTypeId", String.valueOf(contentTypeId));
        params.put("modifiedtime", modifiedSince.format(YYYYMMDD));
        params.put("arrange", "C");
        return call("areaBasedSyncList2", params);
    }

    public TourApiPage areaBasedList(int contentTypeId, int pageNo) {
        Map<String, String> params = page(pageNo, "contentTypeId", String.valueOf(contentTypeId));
        params.put("arrange", "C");
        return call("areaBasedList2", params);
    }

    public TourApiPage searchFestival(LocalDate eventStartDate, LocalDate modifiedSince, int pageNo) {
        Map<String, String> params = page(pageNo, "eventStartDate", eventStartDate.format(YYYYMMDD));
        if (modifiedSince != null) {
            params.put("modifiedtime", modifiedSince.format(YYYYMMDD));
        }
        params.put("arrange", "C");
        return call("searchFestival2", params);
    }

    public Map<String, String> detailCommon(long contentId) {
        return first(call("detailCommon2", single("contentId", String.valueOf(contentId))));
    }

    public Map<String, String> detailIntro(long contentId, int contentTypeId) {
        Map<String, String> params = single("contentId", String.valueOf(contentId));
        params.put("contentTypeId", String.valueOf(contentTypeId));
        return first(call("detailIntro2", params));
    }

    public List<Map<String, String>> detailImages(long contentId) {
        Map<String, String> params = single("contentId", String.valueOf(contentId));
        params.put("imageYN", "Y");
        params.put("numOfRows", "100");
        return call("detailImage2", params).items();
    }

    public Map<String, String> detailPetTour(long contentId) {
        return first(call("detailPetTour2", single("contentId", String.valueOf(contentId))));
    }

    public int todayCallCount() {
        try {
            String value = redisTemplate.opsForValue().get(ContentRedisKeys.tourApiCalls(LocalDate.now(clock)));
            return value == null ? 0 : Integer.parseInt(value);
        } catch (DataAccessException | NumberFormatException e) {
            return 0;
        }
    }

    private TourApiPage call(String operation, Map<String, String> params) {
        URI uri = buildUri(operation, params);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            countCall(operation);
            try {
                String body = restClient.get().uri(uri).retrieve().body(String.class);
                return parse(operation, body);
            } catch (HttpServerErrorException e) {
                if (isQuotaMessage(e.getResponseBodyAsString())) {
                    throw new TourApiQuotaExceededException(operation + " 호출 한도 초과 (" + e.getStatusCode() + ")");
                }
                lastError = e;
            } catch (ResourceAccessException e) {
                lastError = e;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS) || isQuotaMessage(e.getResponseBodyAsString())) {
                    throw new TourApiQuotaExceededException(operation + " 호출 한도 초과 (" + e.getStatusCode() + ")");
                }
                throw new TourApiException(operation + " 요청 거절: " + e.getStatusCode(), e);
            } catch (RestClientException e) {
                throw new TourApiException(operation + " 호출 실패: " + e.getMessage(), e);
            }
            log.warn("TourAPI {} attempt {}/{} failed: {}", operation, attempt, MAX_ATTEMPTS, lastError.getMessage());
            if (attempt < MAX_ATTEMPTS) {
                backoff(attempt);
            }
        }
        throw new TourApiException(operation + " " + MAX_ATTEMPTS + "회 실패: " + lastError.getMessage(), lastError);
    }

    private void countCall(String operation) {
        String key = ContentRedisKeys.tourApiCalls(LocalDate.now(clock));
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, CALL_COUNTER_TTL);
            }
        } catch (DataAccessException e) {
            log.warn("TourAPI call counter unavailable: {}", e.getMessage());
            return;
        }
        if (count != null && count > properties.dailyCallLimit()) {
            throw new TourApiQuotaExceededException(operation + " 일일 호출 한도(" + properties.dailyCallLimit() + ") 도달");
        }
    }

    private void backoff(int attempt) {
        Duration wait = properties.retryBackoff().multipliedBy(attempt);
        if (wait.isZero() || wait.isNegative()) {
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TourApiException("TourAPI 재시도 대기 중 중단됨", e);
        }
    }

    private URI buildUri(String operation, Map<String, String> params) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("serviceKey", properties.serviceKey() == null ? "" : properties.serviceKey());
        all.put("MobileOS", "ETC");
        all.put("MobileApp", "EDGL");
        all.put("_type", "json");
        all.putAll(params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl()).path("/" + operation);
        Map<String, String> variables = new HashMap<>();
        int index = 0;
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String variable = "v" + index++;
            builder.queryParam(entry.getKey(), "{" + variable + "}");
            variables.put(variable, entry.getValue());
        }
        return builder.encode().buildAndExpand(variables).toUri();
    }

    private TourApiPage parse(String operation, String body) {
        if (body == null || body.isBlank()) {
            throw new TourApiException(operation + " 빈 응답");
        }
        if (body.stripLeading().startsWith("<")) {
            log.warn("TourAPI {} returned XML: {}", operation, abbreviate(body));
            throw new TourApiQuotaExceededException(operation + " XML 오류 응답 (한도 초과 또는 키 오류)");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new TourApiException(operation + " 응답을 읽을 수 없음", e);
        }
        JsonNode response = root.path("response");
        if (response.isMissingNode()) {
            if (isQuotaMessage(body) || "22".equals(root.path("resultCode").asString(""))) {
                throw new TourApiQuotaExceededException(operation + " 호출 한도 초과");
            }
            throw new TourApiException(operation + " 오류 응답: " + abbreviate(body));
        }
        JsonNode header = response.path("header");
        String resultCode = header.path("resultCode").asString("");
        if (!OK.equals(resultCode)) {
            String message = header.path("resultMsg").asString("");
            if ("22".equals(resultCode) || isQuotaMessage(message)) {
                throw new TourApiQuotaExceededException(operation + " 호출 한도 초과: " + message);
            }
            throw new TourApiException(operation + " 오류 " + resultCode + ": " + message);
        }
        JsonNode responseBody = response.path("body");
        List<Map<String, String>> items = new ArrayList<>();
        JsonNode item = responseBody.path("items").path("item");
        if (item.isArray()) {
            for (JsonNode node : item) {
                items.add(toMap(node));
            }
        } else if (item.isObject()) {
            items.add(toMap(item));
        }
        return new TourApiPage(items, responseBody.path("pageNo").asInt(1),
                responseBody.path("numOfRows").asInt(items.size()), responseBody.path("totalCount").asInt(items.size()));
    }

    private static Map<String, String> toMap(JsonNode node) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            JsonNode value = field.getValue();
            if (value.isNull() || value.isMissingNode()) {
                continue;
            }
            map.put(field.getKey(), value.isValueNode() ? value.asString() : value.toString());
        }
        return map;
    }

    private static Map<String, String> first(TourApiPage page) {
        return page.items().isEmpty() ? null : page.items().getFirst();
    }

    private static Map<String, String> page(int pageNo, String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        params.put("numOfRows", String.valueOf(PAGE_SIZE));
        params.put("pageNo", String.valueOf(pageNo));
        return params;
    }

    private static Map<String, String> single(String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        return params;
    }

    private static boolean isQuotaMessage(String text) {
        return text != null && text.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS");
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300);
    }
}
