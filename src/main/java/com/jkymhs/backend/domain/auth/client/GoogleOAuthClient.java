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

/**
 * 구글 OAuth 서버 호출 (인가 코드 → 액세스 토큰 → 사용자 정보).
 *
 * 구글 응답 오류는 두 가지로 나눈다.
 * - 4xx: 요청이 잘못됨 (인가 코드 만료·재사용, redirect_uri 불일치 등) → 401, 사용자가 구글 로그인을 다시 하면 된다
 * - 5xx·타임아웃·네트워크 오류: 구글 쪽 문제 → 502, 잠시 후 다시 시도하면 된다
 *
 * RestClient.Builder를 생성자로 받는 이유: 테스트에서 MockRestServiceServer를 붙인 Builder를 넣어
 * 실제 구글 서버 없이 응답을 흉내 낼 수 있게 하기 위해서다. 실제 빈은 GoogleOAuthConfig가 만든다.
 */
@Slf4j
public class GoogleOAuthClient {

    private final RestClient restClient;
    private final GoogleOAuthProperties properties;

    public GoogleOAuthClient(RestClient.Builder restClientBuilder, GoogleOAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /**
     * 인가 코드로 구글 계정 정보를 가져온다. 이메일이 인증되지 않은 구글 계정은 401로 거부한다.
     */
    public GoogleUserInfo fetchUserInfo(String code, String redirectUri) {
        String accessToken = exchangeCode(code, redirectUri);
        GoogleUserInfo userInfo = requestUserInfo(accessToken);

        // FE가 동의 화면에서 email scope를 빼고 요청하면 email이 오지 않는다
        if (userInfo == null || !StringUtils.hasText(userInfo.sub()) || !StringUtils.hasText(userInfo.email())) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "구글 계정 정보(sub·email) 없음");
        }
        // 인증되지 않은 이메일로 가입·연결을 허용하면 남의 이메일로 그 사람 계정에 연결을 시도할 수 있다
        if (!Boolean.TRUE.equals(userInfo.emailVerified())) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "인증되지 않은 구글 이메일");
        }
        return userInfo;
    }

    private String exchangeCode(String code, String redirectUri) {
        // 구글 토큰 API는 JSON이 아니라 form(application/x-www-form-urlencoded)으로 받는다
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        // FE가 구글 동의 화면을 열 때 쓴 redirect_uri와 정확히 같아야 한다. 다르면 구글이 400(redirect_uri_mismatch)을 준다
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
            // 응답 본문은 {"error":"invalid_grant", ...} 형태라 비밀값이 없다. 원인 파악을 위해 로그에 남긴다
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
            // 방금 받은 토큰이 거부되는 경우 (동의 화면에서 openid scope를 빼고 요청 등)
            log.warn("Google userinfo rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "구글 사용자 정보 조회 실패");
        } catch (RestClientException e) {
            log.error("Google userinfo failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR);
        }
    }

    // 토큰 응답에는 expires_in, id_token 등도 있지만 사용자 정보 조회에 필요한 액세스 토큰만 읽는다
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
