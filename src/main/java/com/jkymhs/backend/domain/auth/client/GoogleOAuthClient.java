package com.jkymhs.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
public class GoogleOAuthClient {

    private final RestClient restClient;
    private final GoogleOAuthProperties properties;

    public GoogleOAuthClient(RestClient.Builder restClientBuilder, GoogleOAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public GoogleUserInfo fetchUserInfo(String code, String redirectUri) {
        String accessToken = exchangeCode(code, redirectUri);
        GoogleUserInfo userInfo = requestUserInfo(accessToken);

        if (userInfo == null || !StringUtils.hasText(userInfo.sub()) || !StringUtils.hasText(userInfo.email())) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "구글 계정 정보(sub·email) 없음");
        }
        if (!Boolean.TRUE.equals(userInfo.emailVerified())) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "인증되지 않은 구글 이메일");
        }
        return userInfo;
    }

    private String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("Google token exchange rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "인가 코드 만료·재사용");
        } catch (RestClientException e) {
            log.error("Google token exchange failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        }

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            log.error("Google token response has no access_token");
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        }
        return response.accessToken();
    }

    private GoogleUserInfo requestUserInfo(String accessToken) {
        try {
            return restClient.get()
                    .uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GoogleUserInfo.class);
        } catch (HttpClientErrorException e) {
            log.warn("Google userinfo rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "구글 사용자 정보 조회 실패");
        } catch (RestClientException e) {
            log.error("Google userinfo failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
