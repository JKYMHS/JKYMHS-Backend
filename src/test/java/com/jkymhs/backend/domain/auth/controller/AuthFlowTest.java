package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.VerificationTokenService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입부터 로그아웃까지 실제 API만 순서대로 불러서, 앞 단계에서 받은 토큰이 다음 단계에서 그대로 쓰이는지 확인한다.
 * 인증 메일 API(#3)는 다른 브랜치라서 인증을 마친 상태만 VerificationTokenService로 만든다.
 */
class AuthFlowTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "abc12345";

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("가입 → 로그인 → 재발급 → 로그아웃 → 로그아웃한 토큰으로 재발급하면 401")
    void signupLoginReissueLogout() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        // 가입하면 바로 로그인 상태가 된다
        String signedUp = call("/api/auth/signup", null, """
                {"email": "%s", "verificationToken": "%s", "password": "%s",
                 "consents": {"age14": true, "terms": true, "privacy": true, "behaviorData": false, "emailNotification": false}}
                """.formatted(EMAIL, verificationToken, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 다른 기기에서 로그인
        String loggedIn = call("/api/auth/login", null, """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String reissued = call("/api/auth/reissue", null, refreshTokenBody(loggedIn))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        call("/api/auth/logout", JsonPath.read(reissued, "$.accessToken"), refreshTokenBody(reissued))
                .andExpect(status().isNoContent());

        call("/api/auth/reissue", null, refreshTokenBody(reissued))
                .andExpect(status().isUnauthorized());

        // 로그아웃으로 폐기된 토큰도 "폐기된 토큰의 재사용"이라 탈취로 보고, 가입 때 받은 다른 기기 토큰까지 모두 폐기된다
        assertThat(refreshTokenRepository.findAll()).allMatch(RefreshToken::isRevoked);
        call("/api/auth/reissue", null, refreshTokenBody(signedUp))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions call(String path, String accessToken, String body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return mockMvc.perform(request);
    }

    private static String refreshTokenBody(String responseBody) {
        String refreshToken = JsonPath.read(responseBody, "$.refreshToken");
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }
}
