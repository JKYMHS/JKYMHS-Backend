package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationTokenServiceTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Test
    @DisplayName("발급한 토큰은 한 번만 쓸 수 있다")
    void consumeOnce() {
        String token = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        assertThatCode(() -> verificationTokenService.consume(VerificationPurpose.SIGNUP, EMAIL, token))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> verificationTokenService.consume(VerificationPurpose.SIGNUP, EMAIL, token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("토큰이 다르면 401을 던지고, 원래 토큰은 지우지 않는다")
    void wrongTokenKeepsOriginal() {
        String token = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        assertThatThrownBy(() -> verificationTokenService.consume(VerificationPurpose.SIGNUP, EMAIL, "wrong"))
                .isInstanceOf(CustomException.class);
        assertThatCode(() -> verificationTokenService.consume(VerificationPurpose.SIGNUP, EMAIL, token))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 용도로 받은 토큰은 쓸 수 없다 (회원가입 토큰으로 비밀번호 재설정 불가)")
    void purposeMustMatch() {
        String token = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        assertThatThrownBy(() -> verificationTokenService.consume(VerificationPurpose.RESET_PASSWORD, EMAIL, token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("토큰에는 10분 TTL이 걸린다")
    void ttl() {
        verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        assertThat(redisTemplate.getExpire(AuthRedisKeys.emailVerified(VerificationPurpose.SIGNUP, EMAIL)))
                .isBetween(590L, 600L);
    }
}
