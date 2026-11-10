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

/**
 * 한국관광공사 TourAPI(KorService2) 호출.
 *
 * <ul>
 *   <li>호출 수: 공공데이터포털 개발 계정 키는 하루 호출 수가 정해져 있다. 시도할 때마다 Redis {@code tourapi:calls:{yyyyMMdd}}를 INCR하고,
 *       한도에 닿으면 실제로 부르지 않고 {@link TourApiQuotaExceededException}을 던진다. 서버를 여러 대 띄워도 같은 수를 센다.</li>
 *   <li>한도 초과 응답: 포털은 키 오류·한도 초과를 {@code _type=json}이어도 XML로 준다. XML 본문이나 LIMITED_NUMBER… 메시지는 한도 초과로 본다.</li>
 *   <li>재시도: 5xx와 네트워크 오류(타임아웃 포함)만 최대 3번 시도한다. n번째 재시도 전에 n × {@code retry-backoff}만큼 기다린다.
 *       4xx는 다시 불러도 같은 결과라 바로 실패한다.</li>
 * </ul>
 *
 * 서비스 키는 Decoding 키를 받아서 URI를 만들 때 한 번만 인코딩한다 ('+', '/', '='가 %2B 등으로 바뀐다).
 * Encoding 키를 넣으면 두 번 인코딩돼서 SERVICE_KEY_IS_NOT_REGISTERED 오류가 난다.
 *
 * RestClient.Builder를 생성자로 받는 이유: 테스트에서 MockRestServiceServer를 붙인 Builder를 넣어 실제 TourAPI 없이 응답을 흉내 내기 위해서다.
 * 실제 빈은 {@link TourApiConfig}가 만든다.
 */
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

    /** 법정동 시·도·시·군·구 코드 전체 */
    public TourApiPage ldongCodes(int pageNo) {
        return call("ldongCode2", page(pageNo, "lDongListYn", "Y"));
    }

    /** 신 분류체계 대·중·소 코드 전체 */
    public TourApiPage lclsSystmCodes(int pageNo) {
        return call("lclsSystmCode2", page(pageNo, "lclsSystmListYn", "Y"));
    }

    /**
     * {@code modifiedSince} 날짜 이후에 바뀐 콘텐츠. 내려간 콘텐츠도 showflag=0으로 같이 온다 (showflag를 보내지 않으면 둘 다 준다).
     * 축제 기간(eventstartdate)은 이 응답에 없다.
     */
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

    /**
     * {@code eventStartDate} 이후에 시작하는 축제 (기간 포함).
     *
     * @param modifiedSince null이면 수정일로 거르지 않는다
     */
    public TourApiPage searchFestival(LocalDate eventStartDate, LocalDate modifiedSince, int pageNo) {
        Map<String, String> params = page(pageNo, "eventStartDate", eventStartDate.format(YYYYMMDD));
        if (modifiedSince != null) {
            params.put("modifiedtime", modifiedSince.format(YYYYMMDD));
        }
        params.put("arrange", "C");
        return call("searchFestival2", params);
    }

    /** 소개글·홈페이지 등. 없으면 null */
    public Map<String, String> detailCommon(long contentId) {
        return first(call("detailCommon2", single("contentId", String.valueOf(contentId))));
    }

    /** 운영 시간·쉬는 날·요금 등 (contentTypeId마다 필드 이름이 다르다). 없으면 null */
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

    /** 반려동물 동반 정보. 없으면 null */
    public Map<String, String> detailPetTour(long contentId) {
        return first(call("detailPetTour2", single("contentId", String.valueOf(contentId))));
    }

    /** 오늘 센 호출 수 (Redis가 안 되면 0) */
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

    // 실제로 부르기 전에 센다. 한도에 닿은 뒤의 시도는 TourAPI까지 가지 않는다
    private void countCall(String operation) {
        String key = ContentRedisKeys.tourApiCalls(LocalDate.now(clock));
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, CALL_COUNTER_TTL);
            }
        } catch (DataAccessException e) {
            // 호출 수 집계는 보조 장치다. Redis가 안 되면 세지 않고 부른다 (진짜 한도는 포털이 XML 오류로 알려준다)
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
        // encode() 뒤에 값을 채우면 값 안의 '+', '/', '=' 같은 문자까지 모두 인코딩된다
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
        // 결과가 없으면 items가 ""로, 한 건이면 item이 배열이 아니라 객체로 올 수 있다
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

    // 응답 본문에는 서비스 키가 들어 있지 않아서 로그에 남겨도 된다. 너무 길면 자른다
    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300);
    }
}
