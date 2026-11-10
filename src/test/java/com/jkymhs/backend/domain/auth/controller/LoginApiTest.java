package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.AuthRedisKeys;
import com.jkymhs.backend.domain.auth.service.RateLimitType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginApiTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "abc12345";
    private static final String WRONG_PASSWORD = "wrong1234";
    private static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";
    private static final String LOCKED_MESSAGE = "로그인 시도가 많아 10분간 잠겼습니다.";
    // MockMvc 요청의 기본 접속 IP
    private static final String DEFAULT_IP = "127.0.0.1";

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("로그인에 성공하면 토큰과 온보딩 상태를 주고, 그동안의 실패 기록을 지운다")
    void login() throws Exception {
        registerUser();
        login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());

        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"));

        assertThat(redisTemplate.hasKey(AuthRedisKeys.loginFail(EMAIL))).isFalse();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401이고 실패 횟수가 1 늘어난다")
    void wrongPassword() throws Exception {
        registerUser();

        login(EMAIL, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE));

        assertThat(failCount(EMAIL)).isEqualTo("1");
    }

    @Test
    @DisplayName("없는 이메일도 비밀번호가 틀렸을 때와 똑같이 응답하고 실패 횟수를 센다 (가입 여부를 숨긴다)")
    void unknownEmail() throws Exception {
        registerUser();
        MockHttpServletResponse wrongPassword = login(EMAIL, WRONG_PASSWORD).andReturn().getResponse();

        MockHttpServletResponse unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE))
                .andReturn().getResponse();

        assertThat(unknownEmail.getStatus()).isEqualTo(wrongPassword.getStatus());
        assertThat(description(unknownEmail)).isEqualTo(description(wrongPassword));
        assertThat(failCount("nobody@example.com")).isEqualTo("1");
    }

    @Test
    @DisplayName("비밀번호 없는 구글 전용 계정이면 409이고 실패 횟수에 넣지 않는다")
    void googleOnlyAccount() throws Exception {
        // 구글 가입용 생성 메서드는 #6에서 추가된다. 비밀번호 해시 없이 만들면 같은 상태가 된다
        userService.register(User.createWithPassword(EMAIL, null));

        login(EMAIL, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("구글로 가입한 계정입니다. 구글 로그인을 이용해주세요."));

        assertThat(redisTemplate.hasKey(AuthRedisKeys.loginFail(EMAIL))).isFalse();
    }

    @Test
    @DisplayName("5번째로 틀리면 423이고 description에 잠금 해제 시각을 담는다")
    void lockOnFifthFailure() throws Exception {
        registerUser();
        for (int i = 0; i < 4; i++) {
            login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login(EMAIL, WRONG_PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value(LOCKED_MESSAGE))
                .andExpect(jsonPath("$.description").value(startsWith("잠금 해제 시각: ")));
    }

    @Test
    @DisplayName("잠긴 동안에는 비밀번호가 맞아도 423")
    void lockedEvenWithCorrectPassword() throws Exception {
        registerUser();
        for (int i = 0; i < 5; i++) {
            login(EMAIL, WRONG_PASSWORD);
        }

        login(EMAIL, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value(LOCKED_MESSAGE));

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 IP에서 1분에 20번을 넘으면 21번째부터 429")
    void ipRateLimit() throws Exception {
        registerUser();
        // 계정 잠금(5회)에 먼저 걸리지 않도록 매번 다른 이메일로 시도한다
        for (int i = 0; i < 20; i++) {
            login("user" + i + "@example.com", PASSWORD).andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해주세요."));
        assertThat(redisTemplate.opsForValue().get(AuthRedisKeys.rateLimit(RateLimitType.LOGIN, DEFAULT_IP))).isEqualTo("21");
    }

    private void registerUser() {
        userService.register(User.createWithPassword(EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private Object failCount(String email) {
        return redisTemplate.opsForHash().get(AuthRedisKeys.loginFail(email), "failCount");
    }

    private static String description(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.description");
    }
}
