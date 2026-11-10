package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.security.jwt.JwtProperties;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReissueApiTest extends IntegrationTestSupport {

    private static final String RELOGIN_MESSAGE = "다시 로그인해주세요.";

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private User user;

    @BeforeEach
    void setUp() {
        user = userService.register(User.createWithPassword("user@example.com", "hashed"));
    }

    @Test
    @DisplayName("재발급하면 새 토큰 한 쌍을 주고, 쓴 리프레시 토큰은 폐기한다. 새 리프레시 토큰으로 다시 재발급할 수 있다")
    void reissue() throws Exception {
        IssuedTokens issued = tokenService.issue(user, "device");

        String body = reissue(issued.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.onboardingStatus").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String newRefreshToken = JsonPath.read(body, "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(issued.refreshToken());
        assertThat(jwtProvider.parseUserId(JsonPath.read(body, "$.accessToken"))).isEqualTo(user.getId());

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(2);
        assertThat(tokens).filteredOn(RefreshToken::isRevoked).hasSize(1);

        reissue(newRefreshToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 바꿔 쓴(폐기된) 토큰이 다시 오면 탈취로 보고 401, 그 회원의 모든 기기 토큰을 폐기한다")
    void reuseRevokesAllSessions() throws Exception {
        IssuedTokens deviceA = tokenService.issue(user, "device A");
        IssuedTokens deviceB = tokenService.issue(user, "device B");
        String rotated = JsonPath.read(reissue(deviceA.refreshToken()).andReturn().getResponse().getContentAsString(),
                "$.refreshToken");

        reissue(deviceA.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(RELOGIN_MESSAGE));

        // 401을 던졌어도 "모든 토큰 폐기"는 롤백되지 않고 남아 있어야 한다
        assertThat(refreshTokenRepository.findAll()).hasSize(3).allMatch(RefreshToken::isRevoked);
        reissue(rotated).andExpect(status().isUnauthorized());
        reissue(deviceB.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 401이고, 다른 기기의 토큰은 그대로 쓸 수 있다")
    void expired() throws Exception {
        // 유효기간을 음수로 준 TokenService로 이미 만료된 토큰을 저장한다
        TokenService expiredTokenService = new TokenService(jwtProvider, new JwtProperties(
                jwtProperties.secret(), jwtProperties.accessTokenExpiration(), Duration.ofSeconds(-1)),
                refreshTokenRepository);
        String expired = expiredTokenService.issue(user, "old device").refreshToken();
        IssuedTokens other = tokenService.issue(user, "other device");

        reissue(expired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(RELOGIN_MESSAGE))
                .andExpect(jsonPath("$.description").value("만료·폐기·위조된 리프레시 토큰"));

        reissue(other.refreshToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("저장된 적 없는(위조된) 토큰은 401")
    void unknownToken() throws Exception {
        reissue("not-a-real-refresh-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(RELOGIN_MESSAGE));
    }

    @Test
    @DisplayName("리프레시 토큰을 보내지 않으면 400")
    void missingToken() throws Exception {
        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("리프레시 토큰을 입력해주세요."));
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }
}
