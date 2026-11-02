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

/**
 * 이메일(계정) 기준 로그인 실패 횟수와 잠금 (Redis 키 login:fail:{email}).
 * 로그인 API와 "로그인 중 구글 계정 연결" API의 비밀번호 실패를 같은 키에 합산한다.
 * 없는 이메일도 똑같이 횟수를 세고 잠근다. 그래야 응답만 보고 가입 여부를 알 수 없다.
 *
 * 사용 순서:
 * <pre>
 * LoginAttempt attempt = loginAttemptService.start(email);   // 잠겨 있으면 423
 * if (비밀번호 틀림) { loginAttemptService.recordFailure(attempt); throw 401; }   // 5번째면 423
 * loginAttemptService.reset(email);                          // 성공
 * </pre>
 *
 * 비밀번호를 비교하기 "전에" 시도 1회를 먼저 기록한다.
 * "잠금 확인 → 비밀번호 비교(BCrypt, 약 0.1초) → 틀리면 +1" 순서로 하면, 요청 30개를 동시에 보냈을 때
 * 30개 모두 횟수가 오르기 전에 잠금 확인을 통과해서 5회 제한 없이 30번 맞혀볼 수 있다.
 * 먼저 기록하면 동시에 보내도 6번째 요청부터는 잠금에 걸린다. 맞히면 reset()이 기록을 지운다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    /*
     * 잠금 확인 + 횟수 +1 + TTL(마지막 시도로부터 10분) + 5회째면 잠금을 Redis 안에서 한 번에 실행한다.
     * 시각은 서버마다 시계가 조금씩 다를 수 있어서 Redis 서버 시각(TIME)을 기준으로 쓴다.
     * 반환: {시도 횟수, 잠금 해제 시각(epoch ms, 잠금 아니면 0)}. 이미 잠겨 있으면 시도 횟수 자리에 -1.
     */
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

    /**
     * 비밀번호를 확인하기 전에 부른다. 잠겨 있으면 423을 던지고, 아니면 이번 시도를 1회로 미리 기록한다.
     */
    public LoginAttempt start(String email) {
        List<?> result = redisTemplate.execute(START_SCRIPT, List.of(AuthRedisKeys.loginFail(email)),
                String.valueOf(LOCK_DURATION.toMillis()), String.valueOf(MAX_FAILURES));
        if (result == null || result.size() != 2) {
            // 정상이라면 일어나지 않는다. 기록을 못 했으면 통과시키지 않는다 (보안 기능은 실패하면 막는 쪽으로)
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "일시적인 서버 장애");
        }
        long count = (Long) result.get(0);
        long lockedUntilMillis = (Long) result.get(1);
        if (count < 0) {
            throw locked(lockedUntilMillis);
        }
        return new LoginAttempt(email, count, lockedUntilMillis);
    }

    /**
     * 비밀번호가 틀렸을 때 부른다. 이번 시도가 5번째라 잠겼으면 423을 던지고, 아니면 그냥 돌아온다 (호출한 쪽이 401).
     */
    public void recordFailure(LoginAttempt attempt) {
        if (attempt.lockedUntilMillis() > 0) {
            throw locked(attempt.lockedUntilMillis());
        }
    }

    /**
     * 로그인에 성공하면 실패 기록(과 이번 시도)을 지운다.
     */
    public void reset(String email) {
        redisTemplate.delete(AuthRedisKeys.loginFail(email));
    }

    private static CustomException locked(long lockedUntilMillis) {
        LocalDateTime until = LocalDateTime.ofInstant(Instant.ofEpochMilli(lockedUntilMillis), ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS);
        return new CustomException(ErrorCode.LOGIN_LOCKED, "잠금 해제 시각: " + until);
    }

    /**
     * start()가 기록한 이번 시도.
     *
     * @param count             이번 시도까지 합친 연속 시도 횟수
     * @param lockedUntilMillis 이번 시도로 잠겼으면 잠금 해제 시각(epoch ms), 아니면 0
     */
    public record LoginAttempt(String email, long count, long lockedUntilMillis) {
    }
}
