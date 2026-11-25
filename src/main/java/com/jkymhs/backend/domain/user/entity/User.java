package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.entity.BaseTimeEntity;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.Companion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_google_sub", columnNames = "google_sub")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "varchar(255) collate utf8mb4_bin")
    private String email;

    private String passwordHash;

    private String googleSub;

    private String googleEmail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OnboardingStatus onboardingStatus;

    @Column(length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "last_companion_type", length = 20)
    private Companion lastCompanion;

    private User(String email, String passwordHash, String googleSub, String googleEmail) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.googleSub = googleSub;
        this.googleEmail = googleEmail;
        this.onboardingStatus = OnboardingStatus.NOT_STARTED;
    }

    public static User createWithPassword(String email, String passwordHash) {
        return new User(email, passwordHash, null, null);
    }

    public static User createWithGoogle(String email, String googleSub, String googleEmail) {
        return new User(email, null, googleSub, googleEmail);
    }

    public void linkGoogle(String googleSub, String googleEmail) {
        this.googleSub = googleSub;
        this.googleEmail = googleEmail;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isGoogleLinked() {
        return googleSub != null;
    }

    public String displayName() {
        if (nickname != null) {
            return nickname;
        }
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void completeOnboarding() {
        this.onboardingStatus = OnboardingStatus.DONE;
    }

    public void skipOnboarding() {
        if (onboardingStatus == OnboardingStatus.DONE) {
            throw new CustomException(ErrorCode.ONBOARDING_ALREADY_DONE);
        }
        this.onboardingStatus = OnboardingStatus.SKIPPED;
    }

    public void unlinkGoogle() {
        this.googleSub = null;
        this.googleEmail = null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setInitialPassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void rememberCompanion(Companion companion) {
        this.lastCompanion = companion;
    }
}
