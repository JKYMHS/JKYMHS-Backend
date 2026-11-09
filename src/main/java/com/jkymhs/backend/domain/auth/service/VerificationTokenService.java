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

/**
 * 이메일 인증을 마쳤다는 증거인 일회용 verificationToken (Redis 키 auth:verified:{purpose}:{email}).
 * 인증 코드 검증(#3)에서 발급하고, 회원가입(#4)·비밀번호 재설정에서 확인과 동시에 폐기한다.
 *
 * 이메일 키에 "인증됨"만 저장하면, 인증한 사람과 가입 요청을 보낸 사람이 같은지 알 수 없다.
 * 그래서 인증한 사람에게만 토큰을 주고, 가입할 때 그 토큰을 다시 받는다.
 */
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    /*
     * "값이 같으면 삭제"를 Redis 안에서 한 번에 한다.
     * GET으로 비교한 뒤 자바에서 DEL을 따로 부르면, 같은 토큰으로 동시에 두 번 가입 요청이 왔을 때 둘 다 통과할 수 있다.
     */
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

    /**
     * 토큰이 맞으면 삭제하고, 없거나(만료) 다르면 401을 던진다.
     */
    public void consume(VerificationPurpose purpose, String email, String token) {
        Long deleted = redisTemplate.execute(CONSUME_SCRIPT, List.of(AuthRedisKeys.emailVerified(purpose, email)), token);
        if (deleted == null || deleted == 0) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED, "verificationToken 없음·만료·이메일 불일치");
        }
    }
}
