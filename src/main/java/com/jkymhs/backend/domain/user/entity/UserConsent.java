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

/**
 * 회원 한 명의 약관 동의 한 항목. 회원마다 항목별로 한 행씩, 5행이 생긴다.
 * 동의하지 않은 선택 항목도 agreed = false로 저장해서 "언제 무엇에 동의했는지"를 항목별로 남긴다.
 */
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

    // User.onboardingStatus와 같은 이유로 MySQL ENUM 대신 VARCHAR(30)으로 고정한다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false)
    private boolean agreed;

    // 동의하지 않았으면 null
    private LocalDateTime agreedAt;

    // created_at 컬럼이 없어서 BaseTimeEntity를 상속하지 않는다
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
}
