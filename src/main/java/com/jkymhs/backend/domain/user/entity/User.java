package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.entity.BaseTimeEntity;
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

    @Column(nullable = false)
    private String email;

    // 구글로만 가입한 회원은 비밀번호가 없다 (null)
    private String passwordHash;

    // 구글 계정 고유 ID (id_token의 sub). 구글을 연결하지 않았으면 null
    private String googleSub;

    private String googleEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus onboardingStatus;

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

    // false면 구글로만 가입한 계정이라 이메일·비밀번호 로그인을 할 수 없다
    public boolean hasPassword() {
        return passwordHash != null;
    }
}
