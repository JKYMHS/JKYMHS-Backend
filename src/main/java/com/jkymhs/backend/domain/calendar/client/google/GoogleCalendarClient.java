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

    public void insertEvent(String accessToken, String calendarId, GoogleEvent event) {
        call("일정 추가", () -> restClient.post()
                .uri(properties.apiBaseUrl() + EVENTS, calendarId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(event.body(true, null))
                .retrieve()
                .toBodilessEntity(), false);
    }

    public String eventStatus(String accessToken, String calendarId, String eventId) {
        EventResponse response = call("일정 조회", () -> restClient.get()
                .uri(properties.apiBaseUrl() + EVENT, calendarId, eventId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(EventResponse.class), false);
        return response == null ? null : response.status();
    }

    public void updateEvent(String accessToken, String calendarId, GoogleEvent event) {
        call("일정 수정", () -> restClient.put()
                .uri(properties.apiBaseUrl() + EVENT, calendarId, event.id())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(event.body(false, null))
                .retrieve()
                .toBodilessEntity(), false);
    }

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

    public void moveEvent(String accessToken, String calendarId, String eventId, String destination) {
        call("일정 이동", () -> restClient.post()
                .uri(properties.apiBaseUrl() + MOVE, calendarId, eventId, destination)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity(), false);
    }

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
