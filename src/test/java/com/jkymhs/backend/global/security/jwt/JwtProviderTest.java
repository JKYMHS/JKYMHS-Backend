package com.jkymhs.backend.global.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-jwt-secret-key-at-least-32-characters-long";

    private final JwtProvider jwtProvider = providerWithExpiration(Duration.ofMinutes(30));

    @Test
    @DisplayName("발급한 액세스 토큰에서 회원 ID를 다시 꺼낼 수 있다")
    void createAndParse() {
        String token = jwtProvider.createAccessToken(42L);

        assertThat(jwtProvider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException을 던진다")
    void expiredToken() {
        String token = providerWithExpiration(Duration.ofSeconds(-1)).createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 JwtException을 던진다")
    void wrongSignature() {
        JwtProvider otherProvider = new JwtProvider(new JwtProperties(
                "another-secret-key-that-is-also-32-characters", Duration.ofMinutes(30), Duration.ofDays(14)));
        String token = otherProvider.createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseUserId(token))
                .isInstanceOf(JwtException.class)
                .isNotInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("형식이 깨진 토큰은 JwtException을 던진다")
    void malformedToken() {
        assertThatThrownBy(() -> jwtProvider.parseUserId("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("키 길이와 상관없이 항상 HS256으로 서명한다 (64바이트 키도 HS512가 되지 않는다)")
    void alwaysHs256() {
        JwtProvider longKeyProvider = new JwtProvider(new JwtProperties(
                "a".repeat(64), Duration.ofMinutes(30), Duration.ofDays(14)));
        String token = longKeyProvider.createAccessToken(1L);

        String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    @DisplayName("secret이 32바이트보다 짧으면 만들 때 실패하고, 메시지에 secret 값이 들어가지 않는다")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties("short-secret-value", Duration.ofMinutes(30), Duration.ofDays(14))))
                .isInstanceOf(IllegalStateException.class)
                .message().doesNotContain("short-secret-value");
    }

    private static JwtProvider providerWithExpiration(Duration accessTokenExpiration) {
        return new JwtProvider(new JwtProperties(SECRET, accessTokenExpiration, Duration.ofDays(14)));
    }
}
