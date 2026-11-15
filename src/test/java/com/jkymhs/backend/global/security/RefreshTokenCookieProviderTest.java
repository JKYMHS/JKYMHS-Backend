package com.jkymhs.backend.global.security;

import com.jkymhs.backend.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("발급 쿠키의 속성은 설정값을 따르고, Max-Age는 리프레시 토큰 유효기간이다")
    void create() {
        ResponseCookie cookie = provider(true, "None", Duration.ofDays(1)).create("token");

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("token");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(1));
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    @DisplayName("삭제 쿠키는 값이 비어 있고 Max-Age가 0이며, 나머지 속성은 발급 쿠키와 같다")
    void expire() {
        RefreshTokenCookieProvider provider = provider(false, "Strict", Duration.ofDays(1));

        ResponseCookie created = provider.create("token");
        ResponseCookie expired = provider.expire();

        assertThat(expired.getValue()).isEmpty();
        assertThat(expired.getMaxAge()).isZero();
        assertThat(expired.getName()).isEqualTo(created.getName());
        assertThat(expired.getPath()).isEqualTo(created.getPath());
        assertThat(expired.isSecure()).isFalse();
        assertThat(expired.isHttpOnly()).isTrue();
        assertThat(expired.getSameSite()).isEqualTo("Strict");
    }

    @Test
    @DisplayName("SameSite=None인데 Secure가 꺼져 있으면 서버가 시작되지 않는다")
    void noneWithoutSecureFailsAtStartup() {
        contextRunner
                .withPropertyValues("auth.cookie.secure=false", "auth.cookie.same-site=None")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("auth.cookie.secure는 true여야 합니다"));
    }

    @Test
    @DisplayName("SameSite 값이 Strict, Lax, None이 아니면 서버가 시작되지 않는다")
    void invalidSameSiteFailsAtStartup() {
        contextRunner
                .withPropertyValues("auth.cookie.secure=true", "auth.cookie.same-site=lax")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName(".env에 AUTH_COOKIE_SECURE=처럼 값을 비우면 Secure를 조용히 끄지 않고 시작에 실패한다")
    void blankSecureFailsAtStartup() {
        contextRunner
                .withPropertyValues("auth.cookie.secure=", "auth.cookie.same-site=Lax")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Secure가 켜져 있으면 SameSite=None으로 시작할 수 있다")
    void noneWithSecureStarts() {
        contextRunner
                .withPropertyValues("auth.cookie.secure=true", "auth.cookie.same-site=None")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static RefreshTokenCookieProvider provider(boolean secure, String sameSite, Duration expiration) {
        return new RefreshTokenCookieProvider(
                new AuthCookieProperties(secure, sameSite),
                new JwtProperties("secret", Duration.ofMinutes(30), expiration));
    }

    @EnableConfigurationProperties(AuthCookieProperties.class)
    static class PropertiesConfig {
    }
}
