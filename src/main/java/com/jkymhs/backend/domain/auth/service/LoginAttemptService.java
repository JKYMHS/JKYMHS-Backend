package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 이메일(계정) 기준 로그인 실패 횟수와 잠금 (Redis 키 login:fail:{email}).
 * 로그인 API와 "로그인 중 구글 계정 연결" API의 비밀번호 실패를 같은 키에 합산한다.
 *
 * 없는 이메일도 똑같이 횟수를 세고 잠근다. 그래야 응답만 보고 가입 여부를 알 수 없다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private static final String FAIL_COUNT = "failCount";
    private static final String LOCKED_UNTIL = "lockedUntil";

    private final StringRedisTemplate redisTemplate;

    /**
     * 잠겨 있으면 비밀번호를 확인하기 전에 423을 던진다.
     */
    public void checkNotLocked(String email) {
        Object lockedUntil = redisTemplate.opsForHash().get(AuthRedisKeys.loginFail(email), LOCKED_UNTIL);
        if (lockedUntil == null) {
            return;
        }
        LocalDateTime until = LocalDateTime.parse(lockedUntil.toString());
        if (until.isAfter(LocalDateTime.now())) {
            throw locked(until);
        }
    }

    /**
     * 실패를 1회 기록한다. 이번 실패로 5회가 되면 10분 잠그고 423을 던진다.
     * 5회 미만이면 그냥 돌아오고, 호출한 쪽이 401을 던진다.
     */
    public void recordFailure(String email) {
        String key = AuthRedisKeys.loginFail(email);
        Long failCount = redisTemplate.opsForHash().increment(key, FAIL_COUNT, 1);
        // TTL은 "마지막 실패로부터 10분". 실패할 때마다 다시 10분으로 늘린다
        redisTemplate.expire(key, LOCK_DURATION);

        if (failCount >= MAX_FAILURES) {
            LocalDateTime until = LocalDateTime.now().plus(LOCK_DURATION).truncatedTo(ChronoUnit.SECONDS);
            redisTemplate.opsForHash().put(key, LOCKED_UNTIL, until.toString());
            throw locked(until);
        }
    }

    /**
     * 로그인에 성공하면 실패 기록을 지운다.
     */
    public void reset(String email) {
        redisTemplate.delete(AuthRedisKeys.loginFail(email));
    }

    private CustomException locked(LocalDateTime until) {
        return new CustomException(ErrorCode.LOGIN_LOCKED, "잠금 해제 시각: " + until);
    }
}
