package com.jkymhs.backend.domain.auth.controller;

import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import com.jkymhs.backend.support.RefreshTokenCookies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogoutApiTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userService.register(User.createWithPassword("user@example.com", "hashed"));
    }

    @Test
    @DisplayName("로그아웃하면 204와 삭제 쿠키를 주고 이 기기의 리프레시 토큰만 폐기한다. 다른 기기는 계속 재발급할 수 있다")
    void logout() throws Exception {
        IssuedTokens thisDevice = tokenService.issue(user, "this device");
        IssuedTokens otherDevice = tokenService.issue(user, "other device");

        logout(thisDevice.accessToken(), thisDevice.refreshToken())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(RefreshTokenCookies.cleared());

        assertThat(refreshTokenRepository.findAll())
                .filteredOn(RefreshToken::isRevoked)
                .singleElement()
                .extracting(RefreshToken::getUserAgent).isEqualTo("this device");
        reissue(otherDevice.refreshToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204와 삭제 쿠키를 주고, 어떤 토큰도 폐기하지 않는다")
    void withoutCookie() throws Exception {
        IssuedTokens issued = tokenService.issue(user, "device");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.accessToken()))
                .andExpect(status().isNoContent())
                .andExpect(RefreshTokenCookies.cleared());

        assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("액세스 토큰 없이 로그아웃하면 401이고, 토큰은 폐기되지 않고 쿠키도 지우지 않는다")
    void withoutAccessToken() throws Exception {
        IssuedTokens issued = tokenService.issue(user, "device");

        mockMvc.perform(post("/api/auth/logout").cookie(RefreshTokenCookies.cookie(issued.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(RefreshTokenCookies.absent());

        assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("다른 회원의 리프레시 토큰을 보내도 204만 주고 그 토큰은 건드리지 않는다")
    void otherUsersToken() throws Exception {
        User other = userService.register(User.createWithPassword("other@example.com", "hashed"));
        IssuedTokens mine = tokenService.issue(user, "device");
        IssuedTokens others = tokenService.issue(other, "device");

        logout(mine.accessToken(), others.refreshToken())
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
        reissue(others.refreshToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 로그아웃한 토큰이나 없는 토큰으로 로그아웃해도 204")
    void idempotent() throws Exception {
        IssuedTokens issued = tokenService.issue(user, "device");

        logout(issued.accessToken(), issued.refreshToken()).andExpect(status().isNoContent());
        logout(issued.accessToken(), issued.refreshToken()).andExpect(status().isNoContent());
        logout(issued.accessToken(), "not-a-real-refresh-token").andExpect(status().isNoContent());
        logout(issued.accessToken(), "%ZZ").andExpect(status().isNoContent());
    }

    private ResultActions logout(String accessToken, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .cookie(RefreshTokenCookies.cookie(refreshToken)));
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/reissue").cookie(RefreshTokenCookies.cookie(refreshToken)));
    }
}
