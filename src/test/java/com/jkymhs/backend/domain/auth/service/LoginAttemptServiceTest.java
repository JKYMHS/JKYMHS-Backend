package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Test
    @DisplayName("4번 실패까지는 잠기지 않는다")
    void notLockedBeforeLimit() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        assertThatCode(() -> loginAttemptService.checkNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5번째 실패에서 423을 던지고, description에 잠금 해제 시각을 담는다")
    void lockOnFifthFailure() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        assertThatThrownBy(() -> loginAttemptService.recordFailure(EMAIL))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.LOGIN_LOCKED);
                    assertThat(ce.getDescription()).startsWith("잠금 해제 시각: ");
                    LocalDateTime until = LocalDateTime.parse(ce.getDescription().substring("잠금 해제 시각: ".length()));
                    assertThat(until).isBetween(LocalDateTime.now().plusMinutes(9), LocalDateTime.now().plusMinutes(11));
                });
    }

    @Test
    @DisplayName("잠긴 뒤에는 비밀번호 확인 전에 423을 던진다")
    void checkNotLockedThrowsWhileLocked() {
        lock();

        assertThatThrownBy(() -> loginAttemptService.checkNotLocked(EMAIL))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_LOCKED);
    }

    @Test
    @DisplayName("초기화하면 실패 기록과 잠금이 사라진다")
    void reset() {
        lock();

        loginAttemptService.reset(EMAIL);

        assertThatCode(() -> loginAttemptService.checkNotLocked(EMAIL)).doesNotThrowAnyException();
        assertThat(redisTemplate.hasKey(AuthRedisKeys.loginFail(EMAIL))).isFalse();
    }

    @Test
    @DisplayName("실패 기록에는 10분 TTL이 걸린다")
    void ttl() {
        loginAttemptService.recordFailure(EMAIL);

        assertThat(redisTemplate.getExpire(AuthRedisKeys.loginFail(EMAIL))).isBetween(590L, 600L);
    }

    private void lock() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            try {
                loginAttemptService.recordFailure(EMAIL);
            } catch (CustomException ignored) {
                // 5번째 실패의 423
            }
        }
    }
}
