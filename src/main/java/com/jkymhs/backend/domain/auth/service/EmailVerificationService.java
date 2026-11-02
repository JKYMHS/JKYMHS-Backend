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

/**
 * 이메일 인증 코드 발송과 검증 (Redis 키 auth:code:{purpose}:{email}, Hash {code, failCount, sentAt}).
 * 검증에 성공하면 VerificationTokenService가 회원가입·비밀번호 재설정에 쓸 일회용 토큰을 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000; // 000000 ~ 999999

    private static final String SENT_AT = "sentAt";

    /*
     * 코드 저장(HSET)과 만료 시간(PEXPIRE)을 한 번에 실행한다. 둘 사이에 서버가 죽으면 영원히 만료되지 않는 코드가 남는다.
     * HSET은 같은 필드를 덮어쓰므로, 재발송하면 이전 코드는 바로 무효가 되고 failCount도 0으로 돌아간다.
     */
    private static final RedisScript<Long> SAVE_CODE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'failCount', 0, 'sentAt', ARGV[2])
            return redis.call('PEXPIRE', KEYS[1], ARGV[3])
            """, Long.class);

    /*
     * 비교하기 전에 시도 횟수(failCount)부터 1 올리고, 저장된 코드를 꺼낸다. 키가 없으면 빈 목록을 돌려준다.
     * "코드 읽기 → 비교 → 틀리면 +1" 순서로 나누면, 요청 100개를 동시에 보냈을 때 100개 모두 횟수가 오르기 전에
     * 코드를 읽어서 5회 제한 없이 100번 맞혀볼 수 있다. 먼저 올리면 동시에 보내도 코드 하나당 비교는 5번까지만 일어난다.
     * (키가 없을 때 HINCRBY를 부르면 만료 시간 없는 키가 새로 생기므로 코드가 있는지부터 확인한다)
     */
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

        // 비밀번호 재설정은 가입하지 않은 이메일이어도 코드 저장까지 똑같이 하고 같은 200을 준다. 메일만 안 보낸다.
        // 저장까지 같게 해야 재전송 제한(429)과 검증 응답(400/410)도 똑같아져서, 응답만 보고 가입 여부를 알 수 없다
        if (purpose == VerificationPurpose.RESET_PASSWORD && !registered) {
            return sendResponse();
        }

        try {
            verificationMailSender.send(email, code);
        } catch (MailException e) {
            // 받지 못한 코드 때문에 60초 재전송 제한에 걸리지 않도록 지운다. 사용자는 바로 다시 요청할 수 있다
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

        // 동시에 들어온 다른 요청들이 이미 5번을 다 쓴 경우
        if (attempt > MAX_ATTEMPTS) {
            redisTemplate.delete(key);
            throw codeExpired();
        }

        if (!matches(savedCode, code)) {
            // 5번째 오입력이면 코드를 지운다. 다음 요청부터는 키가 없어서 410이 된다
            if (attempt >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
            }
            throw new CustomException(ErrorCode.VERIFICATION_CODE_MISMATCH, "남은 시도 횟수: " + (MAX_ATTEMPTS - attempt));
        }

        // 같은 코드로 두 요청이 동시에 와도, 키를 실제로 지운 요청 하나만 토큰을 받는다
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
        if (resendableAt.isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS, "60초 재전송 제한");
        }
    }

    // Random은 다음 값을 예측할 수 있어서 인증 코드에는 SecureRandom을 쓴다. 앞자리 0도 살려서 항상 6자리로 만든다
    private static String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    // String.equals는 앞에서부터 다른 글자를 만나면 바로 끝나서, 응답 시간 차이로 몇 글자가 맞았는지 추측할 수 있다.
    // MessageDigest.isEqual은 항상 끝까지 비교한다
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
