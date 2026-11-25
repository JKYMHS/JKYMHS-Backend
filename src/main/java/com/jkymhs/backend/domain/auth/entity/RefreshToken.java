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

import java.time.Duration;
import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private String tokenHash;

    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime rotatedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RefreshToken(User user, String familyId, String tokenHash, String userAgent, LocalDateTime expiresAt) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.userAgent = truncate(userAgent);
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String familyId, String tokenHash, String userAgent, LocalDateTime expiresAt) {
        return new RefreshToken(user, familyId, tokenHash, userAgent, expiresAt);
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

    public void rotate(LocalDateTime now) {
        revoke(now);
        rotatedAt = now;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    public boolean isRotatedWithin(Duration period, LocalDateTime now) {
        return isRotated() && rotatedAt.plus(period).isAfter(now);
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
