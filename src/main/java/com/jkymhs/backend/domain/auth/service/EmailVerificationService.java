package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.dto.response.EmailSendResponse;
import com.jkymhs.backend.domain.auth.dto.response.EmailVerifyResponse;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;

    private static final String SENT_AT = "sentAt";

    private static final RedisScript<Long> SAVE_CODE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'failCount', 0, 'sentAt', ARGV[2])
            return redis.call('PEXPIRE', KEYS[1], ARGV[3])
            """, Long.class);

    private static final RedisScript<List> ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            local code = redis.call('HGET', KEYS[1], 'code')
            if not code then
                return {}
            end
            local attempt = redis.call('HINCRBY', KEYS[1], 'failCount', 1)
            return {attempt, code}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitService rateLimitService;
    private final UserService userService;
    private final VerificationTokenService verificationTokenService;
    private final VerificationMailSender verificationMailSender;

    public EmailSendResponse send(String email, VerificationPurpose purpose, String ip) {
        rateLimitService.check(RateLimitType.EMAIL_SEND, ip);

        boolean registered = userService.existsByEmail(email);
        if (purpose == VerificationPurpose.SIGNUP && registered) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String key = AuthRedisKeys.emailCode(purpose, email);
        checkResendCooldown(key);

        String code = generateCode();
        redisTemplate.execute(SAVE_CODE_SCRIPT, List.of(key),
                code, LocalDateTime.now().toString(), String.valueOf(CODE_TTL.toMillis()));

        if (purpose == VerificationPurpose.RESET_PASSWORD && !registered) {
            return sendResponse();
        }

        try {
            verificationMailSender.send(email, code);
        } catch (MailException e) {
            redisTemplate.delete(key);
            log.error("Verification mail send failed: purpose={}", purpose, e);
            throw new CustomException(ErrorCode.MAIL_SEND_FAILED);
        }
        return sendResponse();
    }

    public EmailVerifyResponse verify(String email, VerificationPurpose purpose, String code) {
        String key = AuthRedisKeys.emailCode(purpose, email);
        List<?> result = redisTemplate.execute(ATTEMPT_SCRIPT, List.of(key));
        if (result == null || result.isEmpty()) {
            throw codeExpired();
        }
        long attempt = (Long) result.get(0);
        String savedCode = (String) result.get(1);

        if (attempt > MAX_ATTEMPTS) {
            throw codeExpired();
        }

        if (!matches(savedCode, code)) {
            if (attempt >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
            }
            throw new CustomException(ErrorCode.VERIFICATION_CODE_MISMATCH, "남은 시도 횟수: " + (MAX_ATTEMPTS - attempt));
        }

        // 키를 실제로 지운 요청 하나만 토큰을 받는다
        if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
            throw codeExpired();
        }
        String token = verificationTokenService.issue(purpose, email);
        return new EmailVerifyResponse(token, VerificationTokenService.TOKEN_TTL.toSeconds());
    }

    private void checkResendCooldown(String key) {
        Object sentAt = redisTemplate.opsForHash().get(key, SENT_AT);
        if (sentAt == null) {
            return;
        }
        LocalDateTime resendableAt = LocalDateTime.parse(sentAt.toString()).plus(RESEND_COOLDOWN);
        Duration remaining = Duration.between(LocalDateTime.now(), resendableAt);
        if (remaining.isPositive()) {
            throw new CustomException(ErrorCode.CODE_RESEND_COOLDOWN, "재전송까지 남은 시간: " + secondsRoundedUp(remaining) + "초");
        }
    }

    static long secondsRoundedUp(Duration duration) {
        return duration.plusNanos(999_999_999).toSeconds();
    }

    private static String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    private static boolean matches(String savedCode, String code) {
        return MessageDigest.isEqual(
                savedCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));
    }

    private static EmailSendResponse sendResponse() {
        return new EmailSendResponse(CODE_TTL.toSeconds(), RESEND_COOLDOWN.toSeconds());
    }

    private static CustomException codeExpired() {
        return new CustomException(ErrorCode.VERIFICATION_CODE_EXPIRED, "5분 경과 또는 오입력 5회 초과");
    }
}
