package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.service.LoginAttemptService.LoginAttempt;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
            fail();
        }

        assertThatCode(() -> loginAttemptService.start(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5번째 실패에서 423을 던지고, description에 잠금 해제 시각을 담는다")
    void lockOnFifthFailure() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            fail();
        }
        LoginAttempt fifth = loginAttemptService.start(EMAIL);

        assertThatThrownBy(() -> loginAttemptService.recordFailure(fifth))
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
    @DisplayName("잠긴 뒤에는 비밀번호를 확인하기 전(start)에 423을 던진다")
    void startThrowsWhileLocked() {
        lock();

        assertThatThrownBy(() -> loginAttemptService.start(EMAIL))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_LOCKED);
    }

    @Test
    @DisplayName("5번째 시도에서 비밀번호를 맞히면 reset으로 잠금이 풀린다")
    void correctPasswordOnFifthAttempt() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            fail();
        }
        loginAttemptService.start(EMAIL);

        loginAttemptService.reset(EMAIL);

        assertThatCode(() -> loginAttemptService.start(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("동시에 30번 시도해도 비밀번호 비교까지 가는 건 5번뿐이다")
    void concurrentAttemptsAreLimited() throws Exception {
        int threads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                ready.await();
                try {
                    loginAttemptService.start(EMAIL);
                    return true;
                } catch (CustomException e) {
                    return false;
                }
            }));
        }
        ready.countDown();

        int passed = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                passed++;
            }
        }
        executor.shutdown();

        assertThat(passed).isEqualTo(LoginAttemptService.MAX_FAILURES);
    }

    @Test
    @DisplayName("대소문자만 다른 이메일도 같은 실패 횟수로 센다")
    void emailCaseDoesNotBypassLock() {
        lock();

        assertThatThrownBy(() -> loginAttemptService.start("User@Example.com"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_LOCKED);
    }

    @Test
    @DisplayName("초기화하면 실패 기록과 잠금이 사라진다")
    void reset() {
        lock();

        loginAttemptService.reset(EMAIL);

        assertThatCode(() -> loginAttemptService.start(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실패 기록에는 10분 TTL이 걸린다")
    void ttl() {
        fail();

        assertThat(redisTemplate.getExpire(AuthRedisKeys.loginFail(EMAIL))).isBetween(590L, 600L);
    }

    private void fail() {
        loginAttemptService.recordFailure(loginAttemptService.start(EMAIL));
    }

    private void lock() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            try {
                fail();
            } catch (CustomException ignored) {
                // 5번째 실패의 423
            }
        }
    }
}
