package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest extends IntegrationTestSupport {

    private static final int RACE_ITERATIONS = 50;
    // 다른 트랜잭션이 잠근 행을 기다리는지 볼 때 "아직 안 끝났다"고 판단하기까지 기다리는 시간
    private static final long LOCK_WAIT_MILLIS = 500;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("액세스 토큰에는 회원 ID가 들어가고, 리프레시 토큰은 원문이 아닌 해시로 저장된다")
    void issue() {
        User user = userService.register(User.createWithPassword("user@example.com", "hashed"));

        IssuedTokens tokens = tokenService.issue(user, "JUnit");

        assertThat(jwtProvider.parseUserId(tokens.accessToken())).isEqualTo(user.getId());

        RefreshToken saved = saved(tokens.refreshToken());
        assertThat(saved.getTokenHash()).isNotEqualTo(tokens.refreshToken()).hasSize(64);
        assertThat(saved.getUserAgent()).isEqualTo("JUnit");
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isBetween(LocalDateTime.now().plusDays(13), LocalDateTime.now().plusDays(14));
    }

    @Test
    @DisplayName("발급할 때마다 다른 리프레시 토큰이 나온다 (기기마다 따로 저장)")
    void differentEachTime() {
        User user = registerUser();

        String first = tokenService.issue(user, null).refreshToken();
        String second = tokenService.issue(user, null).refreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인마다 새 family가 시작되고, 재발급은 일반 교체든 유예 시간 재사용이든 받은 토큰의 family를 물려받는다")
    void familyIsInherited() {
        User user = registerUser();
        String deviceA = tokenService.issue(user, "A").refreshToken();
        String deviceB = tokenService.issue(user, "B").refreshToken();

        String rotated = tokenService.reissue(deviceA, null).refreshToken();
        String replayed = tokenService.reissue(deviceA, null).refreshToken();

        String family = saved(deviceA).getFamilyId();
        assertThat(family).hasSize(36);
        assertThat(saved(rotated).getFamilyId()).isEqualTo(family);
        assertThat(saved(replayed).getFamilyId()).isEqualTo(family);
        assertThat(saved(deviceB).getFamilyId()).isNotEqualTo(family);
    }

    @Test
    @DisplayName("재사용 유예 시간은 10초다")
    void reuseGraceIsTenSeconds() {
        assertThat(TokenService.REUSE_GRACE).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("같은 리프레시 토큰으로 동시에 재발급하면 둘 다 성공하고, 일괄 폐기는 일어나지 않는다")
    void concurrentReissue() throws Exception {
        User user = registerUser();
        String refreshToken = tokenService.issue(user, null).refreshToken();
        String otherDevice = tokenService.issue(user, null).refreshToken();

        // 두 스레드가 준비될 때까지 기다렸다가 동시에 출발시킨다
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<IssuedTokens> task = () -> {
            ready.countDown();
            start.await();
            return tokenService.reissue(refreshToken, null);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<IssuedTokens>> futures = List.of(executor.submit(task), executor.submit(task));
            ready.await();
            start.countDown();

            String first = futures.get(0).get().refreshToken();
            String second = futures.get(1).get().refreshToken();

            assertThat(first).isNotEqualTo(second);
            // 쓴 토큰 하나만 폐기되고, 새로 받은 두 개와 다른 기기 토큰은 살아 있다
            List<RefreshToken> tokens = refreshTokenRepository.findAll();
            assertThat(tokens).hasSize(4);
            assertThat(tokens).filteredOn(RefreshToken::isRevoked).singleElement()
                    .extracting(RefreshToken::getTokenHash).isEqualTo(TokenService.hash(refreshToken));
            assertThat(saved(otherDevice).isRevoked()).isFalse();
        } finally {
            executor.shutdown();
        }
    }

    /*
     * 재발급의 FOR UPDATE를 확인한다. 이 잠금이 없으면 탈취 처리의 일괄 폐기와 겹친 재발급이 폐기 전 상태를 읽고 새 토큰을 만들어서,
     * 모두 폐기했는데 세션 하나가 살아남는다.
     * 일부러 이미 교체된 토큰(유예 시간 경로)으로 확인한다. 일반 교체는 커밋할 때 같은 행을 UPDATE하므로 잠금이 없어도 기다려서 구분이 안 된다.
     */
    @Test
    @DisplayName("재발급은 다른 트랜잭션이 그 토큰 행을 잠그고 있으면 끝날 때까지 기다린다")
    void reissueWaitsForRowLock() throws Exception {
        User user = registerUser();
        String refreshToken = tokenService.issue(user, null).refreshToken();
        tokenService.reissue(refreshToken, null);

        assertWaitsWhileRowIsLocked(refreshToken, () -> tokenService.reissue(refreshToken, null));
    }

    @Test
    @DisplayName("로그아웃은 다른 트랜잭션이 그 토큰 행을 잠그고 있으면 끝날 때까지 기다린다")
    void logoutWaitsForRowLock() throws Exception {
        User user = registerUser();
        String refreshToken = tokenService.issue(user, null).refreshToken();

        assertWaitsWhileRowIsLocked(refreshToken, () -> {
            tokenService.logout(user.getId(), refreshToken);
            return null;
        });
    }

    @Test
    @DisplayName("로그아웃과 유예 시간 재사용이 겹쳐도 어떤 순서로 끝나든 그 로그인의 토큰은 하나도 남지 않는다")
    void concurrentLogoutAndGraceReplay() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < RACE_ITERATIONS; i++) {
                User user = userService.register(User.createWithPassword("race" + i + "@example.com", "hashed"));
                String rotated = tokenService.issue(user, null).refreshToken();
                String current = tokenService.reissue(rotated, null).refreshToken();

                CountDownLatch start = new CountDownLatch(1);
                Future<?> logout = executor.submit(() -> {
                    awaitWithJitter(start);
                    logoutRetryingOnLockConflict(user.getId(), current);
                });
                Future<?> replay = executor.submit(() -> {
                    awaitWithJitter(start);
                    replayIgnoringRejection(rotated);
                });
                start.countDown();
                logout.get(10, TimeUnit.SECONDS);
                replay.get(10, TimeUnit.SECONDS);

                assertThat(activeTokenCount(user)).as("iteration %d", i).isZero();
            }
        }
    }

    /*
     * 잠금을 쥔 트랜잭션 안에서 작업을 다른 스레드로 시작해 안 끝나는지 보고, 커밋해서 잠금을 푼 뒤에 끝나는지 본다.
     * 잠금은 SQL로 직접 건다. findByTokenHashForUpdate로 걸면 그 메서드의 잠금이 사라졌을 때 여기 잠금도 같이 사라져서,
     * 작업이 기다리지 않는 이유가 작업 쪽 문제인지 구분할 수 없다.
     */
    private void assertWaitsWhileRowIsLocked(String refreshToken, Callable<?> work) throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> future = transactionTemplate.execute(status -> {
                jdbcTemplate.queryForList("SELECT id FROM refresh_tokens WHERE token_hash = ? FOR UPDATE",
                        TokenService.hash(refreshToken));
                Future<?> started = executor.submit(work);
                assertThatThrownBy(() -> started.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                return started;
            });

            future.get(10, TimeUnit.SECONDS);
        }
    }

    // 스레드마다 출발을 조금씩 어긋나게 해서 다양한 순서가 나오게 한다
    private static void awaitWithJitter(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        LockSupport.parkNanos(ThreadLocalRandom.current().nextLong(3_000_000));
    }

    // 데드락으로 취소되면 서버는 503을 주고 쿠키를 지우지 않는다. 사용자가 다시 시도하는 상황을 그대로 만든다
    private void logoutRetryingOnLockConflict(Long userId, String refreshToken) {
        try {
            tokenService.logout(userId, refreshToken);
        } catch (CannotAcquireLockException e) {
            tokenService.logout(userId, refreshToken);
        }
    }

    // 로그아웃보다 늦으면 401이 정상이다. 결과는 위 테스트의 마지막 확인으로 본다
    private void replayIgnoringRejection(String refreshToken) {
        try {
            tokenService.reissue(refreshToken, null);
        } catch (CustomException | CannotAcquireLockException ignored) {
        }
    }

    private User registerUser() {
        return userService.register(User.createWithPassword("user@example.com", "hashed"));
    }

    private RefreshToken saved(String refreshToken) {
        String hash = TokenService.hash(refreshToken);
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(hash))
                .findFirst().orElseThrow();
    }

    private long activeTokenCount(User user) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, user.getId());
        return count == null ? 0 : count;
    }
}
