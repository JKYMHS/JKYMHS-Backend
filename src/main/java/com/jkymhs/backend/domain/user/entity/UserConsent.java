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
 * 회원 한 명의 약관 동의 한 항목. 이메일 가입 회원은 가입할 때 항목별로 한 행씩 생긴다.
 * 구글로 가입한 회원은 가입 API가 동의를 받지 않아서 행이 없을 수 있고, 동의 변경(PUT)이 처음 저장할 때 만든다.
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

    /**
     * 동의 여부를 바꾼다. 이미 동의한 항목에 다시 동의하면 처음 동의한 시각을 그대로 둔다
     * ("언제 동의했는지"가 같은 값을 다시 보낼 때마다 바뀌면 기록으로 쓸 수 없다).
     */
    public void change(boolean agreed, LocalDateTime now) {
        if (agreed && !this.agreed) {
            this.agreedAt = now;
        } else if (!agreed) {
            this.agreedAt = null;
        }
        this.agreed = agreed;
    }
}
