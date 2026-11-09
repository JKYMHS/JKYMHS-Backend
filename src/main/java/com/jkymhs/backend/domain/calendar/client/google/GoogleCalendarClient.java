package com.jkymhs.backend.domain.calendar.client.google;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 구글 OAuth(토큰·폐기·사용자 정보)와 구글 캘린더 API v3 호출.
 *
 * 오류를 두 갈래로 낸다.
 * - 연동 화면처럼 사용자가 기다리는 호출(인가 코드 교환·사용자 정보): {@link CustomException} (401 GOOGLE_AUTH_FAILED / 502 GOOGLE_SERVER_ERROR)
 * - 작업자·캘린더 목록이 쓰는 호출: {@link GoogleAuthExpiredException}(invalid_grant), {@link GoogleApiException}(상태 코드)
 *   부르는 쪽이 상태 코드를 보고 다시 시도·실패·재연동 안내를 고른다.
 *
 * 토큰·인가 코드는 로그에 남기지 않는다. 구글 오류 본문({"error": "invalid_grant", ...})에는 비밀값이 없어서 원인 파악용으로 남긴다.
 *
 * calendarId에는 '@'·'#'이 들어간다 (예: "ko.south_korea#holiday@group.v.calendar.google.com").
 * 주소를 문자열로 이어 붙이지 않고 URI 템플릿 변수({calendarId})로 넘겨서 RestClient가 인코딩하게 한다.
 *
 * restClient 필드는 테스트가 MockRestServiceServer를 붙인 것으로 바꿔 끼운다 (RouteApiTest와 같은 방식).
 */
@Slf4j
public class GoogleCalendarClient {

    private static final String CALENDAR_LIST = "/users/me/calendarList?minAccessRole=reader&maxResults=250";
    private static final String EVENTS = "/calendars/{calendarId}/events";
    private static final String EVENT = "/calendars/{calendarId}/events/{eventId}";
    private static final String MOVE = "/calendars/{calendarId}/events/{eventId}/move?destination={destination}";

    private final RestClient restClient;
    private final GoogleCalendarProperties properties;

    public GoogleCalendarClient(RestClient.Builder restClientBuilder, GoogleCalendarProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    // ---------- OAuth ----------

    /** 인가 코드 → 토큰. 4xx(코드 만료·재사용·redirect_uri 불일치)는 401, 5xx·네트워크는 502 */
    public GoogleTokens exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        TokenResponse response;
        try {
            response = restClient.post().uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("구글 캘린더 인가 코드 교환 거부: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "캘린더 인가 코드 만료·재사용");
            }
            log.error("구글 캘린더 인가 코드 교환 실패: {}", e.getStatusCode());
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        } catch (RestClientException e) {
            log.error("구글 캘린더 인가 코드 교환 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        }
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR, "토큰 응답에 access_token 없음");
        }
        return new GoogleTokens(response.accessToken(), response.refreshToken(), response.scope());
    }

    /** 액세스 토큰으로 구글 계정 이메일을 읽는다 (연동 화면에 "누구 계정으로 연동됐는지" 보여준다) */
    public String fetchEmail(String accessToken) {
        UserInfo userInfo;
        try {
            userInfo = restClient.get().uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(UserInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("구글 사용자 정보 거부: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new CustomException(ErrorCode.CALENDAR_PERMISSION_DENIED, "이메일 권한 없음");
            }
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        } catch (RestClientException e) {
            log.error("구글 사용자 정보 조회 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        }
        if (userInfo == null || !StringUtils.hasText(userInfo.email())) {
            throw new CustomException(ErrorCode.CALENDAR_PERMISSION_DENIED, "구글 이메일 없음 (email scope 누락)");
        }
        return userInfo.email();
    }

    /**
     * 리프레시 토큰 → 새 액세스 토큰 (1시간짜리).
     *
     * @throws GoogleAuthExpiredException 토큰이 폐기·만료됨 (invalid_grant). 다시 연동해야 한다
     * @throws GoogleApiException         그 밖의 실패 (5xx·네트워크 → 나중에 다시)
     */
    public String refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        TokenResponse response = call("토큰 갱신", () -> restClient.post().uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class), true);
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new GoogleApiException(502, "", "토큰 응답에 access_token 없음");
        }
        return response.accessToken();
    }

    /** 토큰 폐기 (연동 해제·탈퇴). 이미 폐기됐거나 구글이 응답하지 않아도 해제는 계속해야 해서 오류를 무시한다 */
    public void revoke(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        try {
            restClient.post().uri(properties.revokeUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.info("구글 토큰 폐기 실패(무시): {}", e.getMessage());
        }
    }

    // ---------- Calendar API ----------

    /** 사용자의 캘린더 목록 (쓰기 가능 여부는 accessRole로 판단) */
    public List<GoogleCalendarEntry> calendarList(String accessToken) {
        CalendarListResponse response = call("캘린더 목록", () -> restClient.get()
                .uri(properties.apiBaseUrl() + CALENDAR_LIST)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(CalendarListResponse.class), false);
        List<GoogleCalendarEntry> entries = new ArrayList<>();
        if (response != null && response.items() != null) {
            for (CalendarListResponse.Entry item : response.items()) {
                String name = StringUtils.hasText(item.summaryOverride()) ? item.summaryOverride() : item.summary();
                entries.add(new GoogleCalendarEntry(item.id(), name, Boolean.TRUE.equals(item.primary()),
                        item.accessRole()));
            }
        }
        return entries;
    }

    /** 새 일정. id를 우리가 정해서 보낸다 ("edgl" + 일정 항목 id) — 같은 항목을 두 번 올려도 일정이 두 개 생기지 않는다 (409) */
    public void insertEvent(String accessToken, String calendarId, GoogleEvent event) {
        call("일정 추가", () -> restClient.post()
                .uri(properties.apiBaseUrl() + EVENTS, calendarId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(event.body(true, null))
                .retrieve()
                .toBodilessEntity(), false);
    }

    /** 일정 상태 ("confirmed" / "cancelled"). 지운 일정도 한동안 cancelled로 남아 있어서 같은 id로 새로 만들 수 없다 */
    public String eventStatus(String accessToken, String calendarId, String eventId) {
        EventResponse response = call("일정 조회", () -> restClient.get()
                .uri(properties.apiBaseUrl() + EVENT, calendarId, eventId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(EventResponse.class), false);
        return response == null ? null : response.status();
    }

    /** 일정 내용 전체를 바꾼다 (PUT) */
    public void updateEvent(String accessToken, String calendarId, GoogleEvent event) {
        call("일정 수정", () -> restClient.put()
                .uri(properties.apiBaseUrl() + EVENT, calendarId, event.id())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(event.body(false, null))
                .retrieve()
                .toBodilessEntity(), false);
    }

    /** 지워진(cancelled) 일정을 되살리면서 내용도 맞춘다 (PATCH status=confirmed) */
    public void reviveEvent(String accessToken, String calendarId, GoogleEvent event) {
        call("일정 되살리기", () -> restClient.patch()
                .uri(properties.apiBaseUrl() + EVENT, calendarId, event.id())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(event.body(false, "confirmed"))
                .retrieve()
                .toBodilessEntity(), false);
    }

    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        call("일정 삭제", () -> restClient.delete()
                .uri(properties.apiBaseUrl() + EVENT, calendarId, eventId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity(), false);
    }

    /** 다른 캘린더로 옮긴다. 일정 id는 그대로다 */
    public void moveEvent(String accessToken, String calendarId, String eventId, String destination) {
        call("일정 이동", () -> restClient.post()
                .uri(properties.apiBaseUrl() + MOVE, calendarId, eventId, destination)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity(), false);
    }

    // ---------- 공통 ----------

    /**
     * HTTP 오류를 {@link GoogleApiException}으로 바꾼다. 토큰 API(tokenCall)의 400 invalid_grant만
     * {@link GoogleAuthExpiredException}으로 따로 낸다.
     */
    private <T> T call(String action, Supplier<T> request, boolean tokenCall) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            int status = e.getStatusCode().value();
            if (tokenCall && status >= 400 && status < 500 && body.contains("invalid_grant")) {
                log.info("구글 리프레시 토큰 거부(invalid_grant)");
                throw new GoogleAuthExpiredException("invalid_grant");
            }
            log.warn("구글 캘린더 {} 실패: {} {}", action, status, body);
            throw new GoogleApiException(status, body, action + " 실패: " + status);
        } catch (RestClientException e) {
            log.warn("구글 캘린더 {} 실패(응답 없음): {}", action, e.getMessage());
            throw new GoogleApiException(0, "", action + " 실패: " + e.getMessage());
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("scope") String scope
    ) {
    }

    private record UserInfo(String email) {
    }

    private record CalendarListResponse(List<Entry> items) {

        private record Entry(String id, String summary, String summaryOverride, Boolean primary, String accessRole) {
        }
    }

    private record EventResponse(String id, String status) {
    }
}
