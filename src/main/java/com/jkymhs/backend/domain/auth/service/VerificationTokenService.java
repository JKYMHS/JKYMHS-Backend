package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public String issue(VerificationPurpose purpose, String email) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(AuthRedisKeys.emailVerified(purpose, email), token, TOKEN_TTL);
        return token;
    }

    public void consume(VerificationPurpose purpose, String email, String token) {
        Long deleted = redisTemplate.execute(CONSUME_SCRIPT, List.of(AuthRedisKeys.emailVerified(purpose, email)), token);
        if (deleted == null || deleted == 0) {
            throw notVerified();
        }
    }

    public static CustomException notVerified() {
        return new CustomException(ErrorCode.EMAIL_NOT_VERIFIED, "verificationToken 없음·만료·이메일 불일치");
    }
}
