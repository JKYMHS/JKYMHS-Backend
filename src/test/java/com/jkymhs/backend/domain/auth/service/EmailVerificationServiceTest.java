package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 코드로 검증 요청이 동시에 몰릴 때도 5회 제한과 일회용 규칙이 지켜지는지 확인한다.
 */
class EmailVerificationServiceTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final int CONCURRENT_REQUESTS = 20;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Test
    @DisplayName("틀린 코드를 동시에 20번 보내도 비교는 5번만 하고(400), 나머지는 410으로 막는다")
    void concurrentWrongCodes() throws Exception {
        String code = sendCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        List<ErrorCode> results = verifyConcurrently(wrong);

        assertThat(results).filteredOn(ErrorCode.VERIFICATION_CODE_MISMATCH::equals)
                .hasSize(EmailVerificationService.MAX_ATTEMPTS);
        assertThat(results).filteredOn(ErrorCode.VERIFICATION_CODE_EXPIRED::equals)
                .hasSize(CONCURRENT_REQUESTS - EmailVerificationService.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("맞는 코드를 동시에 20번 보내도 토큰은 한 번만 발급한다")
    void concurrentCorrectCodes() throws Exception {
        String code = sendCode();

        List<ErrorCode> results = verifyConcurrently(code);

        // null = 성공
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
        assertThat(results).filteredOn(ErrorCode.VERIFICATION_CODE_EXPIRED::equals).hasSize(CONCURRENT_REQUESTS - 1);
    }

    private String sendCode() {
        emailVerificationService.send(EMAIL, VerificationPurpose.SIGNUP, "127.0.0.1");
        return redisTemplate.opsForHash()
                .get(AuthRedisKeys.emailCode(VerificationPurpose.SIGNUP, EMAIL), "code").toString();
    }

    // 모든 스레드를 출발선(latch)에 세워 두었다가 동시에 출발시킨다. 성공하면 null, 실패하면 에러 코드를 모은다
    private List<ErrorCode> verifyConcurrently(String code) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        emailVerificationService.verify(EMAIL, VerificationPurpose.SIGNUP, code);
                        return null;
                    } catch (CustomException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            start.countDown();
        }
        List<ErrorCode> results = new ArrayList<>();
        for (Future<ErrorCode> future : futures) {
            results.add(future.get());
        }
        return results;
    }
}
