package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * IP 기준 요청 횟수 제한 (로그인 1분 20회, 인증 메일 1분 5회).
 * Redis가 죽으면 예외가 그대로 올라가 503이 된다. 보안 기능이라 제한 없이 통과시키지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /*
     * INCR(횟수 +1)과 PEXPIRE(만료 시간 설정)를 Lua 스크립트로 묶어서 Redis 안에서 한 번에 실행한다.
     * 자바에서 두 번 나눠 호출하면 그 사이에 서버가 죽었을 때 만료 시간 없는 키가 남아서 그 IP가 영원히 막힌다.
     */
    private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void check(RateLimitType type, String ip) {
        String key = AuthRedisKeys.rateLimit(type, ip);
        Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(type.getWindow().toMillis()));
        if (count == null) {
            // 정상이라면 일어나지 않는다. 횟수를 못 셌으면 통과시키지 않는다 (보안 기능은 실패하면 막는 쪽으로)
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "일시적인 서버 장애");
        }
        if (count > type.getLimit()) {
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS, "IP 요청 제한 초과");
        }
    }
}
