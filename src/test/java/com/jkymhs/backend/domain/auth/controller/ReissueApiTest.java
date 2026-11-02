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
import com.jkymhs.backend.support.RefreshTokenCookies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReissueApiTest extends IntegrationTestSupport {

    private static final String RELOGIN_MESSAGE = "다시 로그인해주세요.";
    private static final String INVALID_DESCRIPTION = "만료·폐기·위조된 리프레시 토큰 또는 쿠키 없음";

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
    @DisplayName("재발급하면 본문에 액세스 토큰, 쿠키에 새 리프레시 토큰을 주고 쓴 토큰은 폐기한다. 새 쿠키로 다시 재발급할 수 있다")
    void reissue() throws Exception {
        IssuedTokens issued = tokenService.issue(user, "device");

        MvcResult result = reissue(issued.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.onboardingStatus").doesNotExist())
                .andExpect(RefreshTokenCookies.issued())
                .andReturn();

        String newRefreshToken = RefreshTokenCookies.tokenOf(result);
        assertThat(newRefreshToken).isNotEqualTo(issued.refreshToken());
        assertThat(jwtProvider.parseUserId(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken")))
                .isEqualTo(user.getId());

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
        String rotated = RefreshTokenCookies.tokenOf(reissue(deviceA.refreshToken()).andReturn());

        reissue(deviceA.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value(RELOGIN_MESSAGE))
                .andExpect(RefreshTokenCookies.absent());

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
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value(RELOGIN_MESSAGE))
                .andExpect(jsonPath("$.description").value(INVALID_DESCRIPTION));

        reissue(other.refreshToken()).andExpect(status().isOk());
    }

    // %ZZ는 잘못된 URL 인코딩이다. 쿠키 값은 사용자가 마음대로 넣을 수 있으므로 500이 나면 안 된다
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"not-a-real-refresh-token", "%ZZ"})
    @DisplayName("저장된 적 없는(위조된) 토큰은 401")
    void unknownToken(String forged) throws Exception {
        reissue(forged)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value(RELOGIN_MESSAGE));
    }

    @Test
    @DisplayName("쿠키 없이 재발급하면 401")
    void missingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.description").value(INVALID_DESCRIPTION))
                .andExpect(RefreshTokenCookies.absent());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " "})
    @DisplayName("쿠키 값이 비어 있어도 401")
    void blankCookie(String blank) throws Exception {
        reissue(blank)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.description").value(INVALID_DESCRIPTION));
    }

    @Test
    @DisplayName("예전 방식으로 본문에 토큰을 보내도 읽지 않는다. 쿠키가 없으면 401")
    void bodyIsIgnored() throws Exception {
        IssuedTokens issued = tokenService.issue(user, "device");

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(issued.refreshToken())))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/reissue").cookie(RefreshTokenCookies.cookie(refreshToken)));
    }
}
