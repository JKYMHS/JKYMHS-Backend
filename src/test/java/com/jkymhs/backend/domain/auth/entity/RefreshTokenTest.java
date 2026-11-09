package com.jkymhs.backend.domain.auth.entity;

import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private final User user = User.createWithPassword("user@example.com", "hashed");
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Test
    @DisplayName("만료 시각이 지나면 만료로 본다")
    void expired() {
        RefreshToken token = RefreshToken.issue(user, "hash", null, now.plusDays(14));

        assertThat(token.isExpired(now)).isFalse();
        assertThat(token.isExpired(now.plusDays(14))).isTrue();
    }

    @Test
    @DisplayName("폐기하면 폐기 시각이 남고, 다시 폐기해도 처음 시각을 유지한다")
    void revoke() {
        RefreshToken token = RefreshToken.issue(user, "hash", null, now.plusDays(14));

        token.revoke(now);
        token.revoke(now.plusHours(1));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("255자가 넘는 User-Agent는 잘라서 저장한다")
    void truncateUserAgent() {
        RefreshToken token = RefreshToken.issue(user, "hash", "a".repeat(300), now.plusDays(14));

        assertThat(token.getUserAgent()).hasSize(255);
    }
}
