package com.jkymhs.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "user_consents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_consents_user_id_consent_type", columnNames = {"user_id", "consent_type"})
})
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false)
    private boolean agreed;

    private LocalDateTime agreedAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private UserConsent(User user, ConsentType consentType, boolean agreed, LocalDateTime now) {
        this.user = user;
        this.consentType = consentType;
        this.agreed = agreed;
        this.agreedAt = agreed ? now : null;
    }

    public static UserConsent create(User user, ConsentType consentType, boolean agreed, LocalDateTime now) {
        return new UserConsent(user, consentType, agreed, now);
    }

    public void change(boolean agreed, LocalDateTime now) {
        if (agreed && !this.agreed) {
            this.agreedAt = now;
        } else if (!agreed) {
            this.agreedAt = null;
        }
        this.agreed = agreed;
    }
}
