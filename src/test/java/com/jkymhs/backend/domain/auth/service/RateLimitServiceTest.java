package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest extends IntegrationTestSupport {

    private static final String IP = "203.0.113.1";

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    @DisplayName("한도까지는 통과하고, 한도를 넘는 요청부터 429를 던진다")
    void blockAfterLimit() {
        for (int i = 0; i < RateLimitType.EMAIL_SEND.getLimit(); i++) {
            rateLimitService.check(RateLimitType.EMAIL_SEND, IP);
        }

        assertThatThrownBy(() -> rateLimitService.check(RateLimitType.EMAIL_SEND, IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("첫 요청 때 1분 TTL이 걸린다 (만료 시간 없는 키가 남지 않는다)")
    void setsTtlOnFirstRequest() {
        rateLimitService.check(RateLimitType.LOGIN, IP);

        Long ttlSeconds = redisTemplate.getExpire(AuthRedisKeys.rateLimit(RateLimitType.LOGIN, IP));
        assertThat(ttlSeconds).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("용도(LOGIN / EMAIL_SEND)와 IP가 다르면 따로 센다")
    void countsSeparately() {
        for (int i = 0; i < RateLimitType.EMAIL_SEND.getLimit(); i++) {
            rateLimitService.check(RateLimitType.EMAIL_SEND, IP);
        }

        assertThatCode(() -> rateLimitService.check(RateLimitType.LOGIN, IP)).doesNotThrowAnyException();
        assertThatCode(() -> rateLimitService.check(RateLimitType.EMAIL_SEND, "203.0.113.2")).doesNotThrowAnyException();
    }
}
