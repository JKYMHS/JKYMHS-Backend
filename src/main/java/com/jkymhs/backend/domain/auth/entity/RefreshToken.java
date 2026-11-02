package com.jkymhs.backend.domain.auth.entity;

import com.jkymhs.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 기기(로그인 세션)마다 하나씩 생기는 리프레시 토큰.
 * DB가 유출돼도 토큰을 그대로 쓸 수 없도록 원문 대신 SHA-256 해시만 저장한다.
 * 폐기할 때 행을 지우지 않고 revokedAt을 채운다. 폐기된 토큰이 다시 오면 탈취로 판단하기 위해서다.
 */
@Getter
@Entity
@Table(name = "refresh_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash")
})
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    private static final int USER_AGENT_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String tokenHash;

    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    // users와 달리 updated_at 컬럼이 없어서 BaseTimeEntity를 상속하지 않는다
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RefreshToken(User user, String tokenHash, String userAgent, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.userAgent = truncate(userAgent);
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String tokenHash, String userAgent, LocalDateTime expiresAt) {
        return new RefreshToken(user, tokenHash, userAgent, expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    // 브라우저 User-Agent는 255자를 넘을 수 있어서 컬럼 길이에 맞게 자른다
    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
