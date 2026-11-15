package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.AuthRedisKeys;
import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.auth.service.VerificationTokenService;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetApiTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String OLD_PASSWORD = "abc12345";
    private static final String NEW_PASSWORD = "new12345";
    private static final String WRONG_PASSWORD = "wrong1234";
    private static final String NOT_VERIFIED_MESSAGE = "이메일 인증이 만료되었습니다. 다시 인증해주세요.";

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("재설정하면 204이고, 새 비밀번호로만 로그인되며 모든 기기의 리프레시 토큰과 로그인 잠금이 지워진다")
    void reset() throws Exception {
        User user = registerUser();
        // 두 기기에서 로그인한 상태
        tokenService.issue(user, "phone");
        tokenService.issue(user, "laptop");
        // 비밀번호를 5번 틀려서 잠긴 상태
        for (int i = 0; i < 5; i++) {
            login(EMAIL, WRONG_PASSWORD);
        }
        assertThat(redisTemplate.hasKey(AuthRedisKeys.loginFail(EMAIL))).isTrue();
        String token = verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, EMAIL);

        reset(EMAIL, token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(redisTemplate.hasKey(AuthRedisKeys.loginFail(EMAIL))).isFalse();
        login(EMAIL, OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 토큰으로 두 번 재설정하면 두 번째는 401 (토큰은 한 번 쓰면 폐기된다)")
    void tokenIsSingleUse() throws Exception {
        registerUser();
        String token = verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, EMAIL);
        reset(EMAIL, token, NEW_PASSWORD).andExpect(status().isNoContent());

        reset(EMAIL, token, "other1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, findUser().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("토큰이 틀리거나 회원가입용 토큰이면 401이고 비밀번호는 그대로다")
    void invalidToken() throws Exception {
        registerUser();
        verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, EMAIL);
        String signupToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        reset(EMAIL, "not-the-token", NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value(NOT_VERIFIED_MESSAGE));
        reset(EMAIL, signupToken, NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(passwordEncoder.matches(OLD_PASSWORD, findUser().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("가입하지 않은 이메일은 토큰이 맞아도, 토큰이 틀렸을 때와 응답 본문까지 같은 401 (가입 여부를 숨긴다)")
    void unknownEmail() throws Exception {
        registerUser();
        String unknownEmailToken = verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, "nobody@example.com");

        Map<String, Object> unknownEmail = errorBody(reset("nobody@example.com", unknownEmailToken, NEW_PASSWORD)
                .andExpect(status().isUnauthorized()));
        Map<String, Object> wrongToken = errorBody(reset(EMAIL, "not-the-token", NEW_PASSWORD)
                .andExpect(status().isUnauthorized()));

        assertThat(unknownEmail).isEqualTo(wrongToken);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("구글로만 가입한 회원도 재설정하면 비밀번호가 생기고 이메일·비밀번호로 로그인된다")
    void googleOnlyAccount() throws Exception {
        userService.register(User.createWithGoogle(EMAIL, "google-sub", EMAIL));
        String token = verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, EMAIL);

        reset(EMAIL, token, NEW_PASSWORD).andExpect(status().isNoContent());

        User user = findUser();
        assertThat(user.hasPassword()).isTrue();
        assertThat(user.isGoogleLinked()).isTrue();
        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 비밀번호가 규칙에 맞지 않으면 400이고 토큰은 폐기되지 않아서, 고쳐서 다시 보내면 된다")
    void invalidPasswordKeepsToken() throws Exception {
        registerUser();
        String token = verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, EMAIL);

        reset(EMAIL, token, "onlyletters")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요."));

        reset(EMAIL, token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("이메일 대소문자·앞뒤 공백은 인증 때와 같은 기준으로 맞춰서 처리한다")
    void normalizesEmail() throws Exception {
        registerUser();
        String token = verificationTokenService.issue(VerificationPurpose.RESET_PASSWORD, EMAIL);

        reset("  User@Example.COM ", token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches(NEW_PASSWORD, findUser().getPasswordHash())).isTrue();
    }

    private User registerUser() {
        return userService.register(User.createWithPassword(EMAIL, passwordEncoder.encode(OLD_PASSWORD)));
    }

    private User findUser() {
        return userRepository.findByEmail(EMAIL).orElseThrow();
    }

    private ResultActions reset(String email, String verificationToken, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "verificationToken": "%s", "newPassword": "%s"}
                        """.formatted(email, verificationToken, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    // 응답마다 다른 timestamp만 빼고 비교한다
    private static Map<String, Object> errorBody(ResultActions result) throws Exception {
        Map<String, Object> body = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$");
        body.remove("timestamp");
        return body;
    }
}
