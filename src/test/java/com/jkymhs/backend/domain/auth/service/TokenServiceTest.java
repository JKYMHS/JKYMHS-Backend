package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest extends IntegrationTestSupport {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("액세스 토큰에는 회원 ID가 들어가고, 리프레시 토큰은 원문이 아닌 해시로 저장된다")
    void issue() {
        User user = userService.register(User.createWithPassword("user@example.com", "hashed"));

        TokenService.IssuedTokens tokens = tokenService.issue(user, "JUnit");

        assertThat(jwtProvider.parseUserId(tokens.accessToken())).isEqualTo(user.getId());

        RefreshToken saved = refreshTokenRepository.findByTokenHash(TokenService.hash(tokens.refreshToken())).orElseThrow();
        assertThat(saved.getTokenHash()).isNotEqualTo(tokens.refreshToken()).hasSize(64);
        assertThat(saved.getUserAgent()).isEqualTo("JUnit");
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isBetween(LocalDateTime.now().plusDays(13), LocalDateTime.now().plusDays(14));
    }

    @Test
    @DisplayName("발급할 때마다 다른 리프레시 토큰이 나온다 (기기마다 따로 저장)")
    void differentEachTime() {
        User user = userService.register(User.createWithPassword("user@example.com", "hashed"));

        String first = tokenService.issue(user, null).refreshToken();
        String second = tokenService.issue(user, null).refreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }
}
