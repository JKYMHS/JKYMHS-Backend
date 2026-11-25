package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private static final RedisScript<List> START_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local lockMillis = tonumber(ARGV[1])
            local maxFailures = tonumber(ARGV[2])

            local lockedUntil = tonumber(redis.call('HGET', KEYS[1], 'lockedUntil'))
            if lockedUntil then
                if lockedUntil > now then
                    return {-1, lockedUntil}
                end
                -- 잠금 시간이 지났으면 처음부터 다시 센다
                redis.call('DEL', KEYS[1])
            end

            local count = redis.call('HINCRBY', KEYS[1], 'failCount', 1)
            redis.call('PEXPIRE', KEYS[1], lockMillis)
            if count >= maxFailures then
                lockedUntil = now + lockMillis
                redis.call('HSET', KEYS[1], 'lockedUntil', lockedUntil)
                return {count, lockedUntil}
            end
            return {count, 0}
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public LoginAttempt start(String email) {
        List<?> result = redisTemplate.execute(START_SCRIPT, List.of(AuthRedisKeys.loginFail(email)),
                String.valueOf(LOCK_DURATION.toMillis()), String.valueOf(MAX_FAILURES));
        if (result == null || result.size() != 2) {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "일시적인 서버 장애");
        }
        long count = (Long) result.get(0);
        long lockedUntilMillis = (Long) result.get(1);
        if (count < 0) {
            throw locked(lockedUntilMillis);
        }
        return new LoginAttempt(email, count, lockedUntilMillis);
    }

    public void recordFailure(LoginAttempt attempt) {
        if (attempt.lockedUntilMillis() > 0) {
            throw locked(attempt.lockedUntilMillis());
        }
    }

    public void reset(String email) {
        redisTemplate.delete(AuthRedisKeys.loginFail(email));
    }

    private static CustomException locked(long lockedUntilMillis) {
        LocalDateTime until = LocalDateTime.ofInstant(Instant.ofEpochMilli(lockedUntilMillis), ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS);
        return new CustomException(ErrorCode.LOGIN_LOCKED, "잠금 해제 시각: " + until);
    }

    public record LoginAttempt(String email, long count, long lockedUntilMillis) {
    }
}
