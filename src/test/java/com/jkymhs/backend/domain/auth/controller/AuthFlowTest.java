package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.VerificationTokenService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import com.jkymhs.backend.support.RefreshTokenCookies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입부터 로그아웃까지 실제 API만 순서대로 불러서, 앞 단계에서 받은 토큰·쿠키가 다음 단계에서 그대로 쓰이는지 확인한다.
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
    @DisplayName("가입 → 로그인 → 재발급 → 로그아웃 → 로그아웃한 토큰으로 재발급하면 401이고, 가입 때 받은 다른 기기 토큰은 살아 있다")
    void signupLoginReissueLogout() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        // 가입하면 바로 로그인 상태가 된다
        MvcResult signedUp = call("/api/auth/signup", """
                {"email": "%s", "verificationToken": "%s", "password": "%s",
                 "consents": {"age14": true, "terms": true, "privacy": true, "behaviorData": false, "emailNotification": false}}
                """.formatted(EMAIL, verificationToken, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn();

        // 다른 기기에서 로그인
        MvcResult loggedIn = call("/api/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult reissued = reissue(RefreshTokenCookies.tokenOf(loggedIn))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = RefreshTokenCookies.tokenOf(reissued);
        String accessToken = JsonPath.read(reissued.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(RefreshTokenCookies.cookie(refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(RefreshTokenCookies.cleared());

        reissue(refreshToken).andExpect(status().isUnauthorized());

        // 로그아웃한 토큰이 다시 온 것은 탈취가 아니다. 가입 때 받은 다른 기기 토큰은 폐기되지 않는다
        assertThat(refreshTokenRepository.findAll()).filteredOn(token -> !token.isRevoked()).hasSize(1);
        reissue(RefreshTokenCookies.tokenOf(signedUp)).andExpect(status().isOk());
    }

    private ResultActions call(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/reissue").cookie(RefreshTokenCookies.cookie(refreshToken)));
    }
}
