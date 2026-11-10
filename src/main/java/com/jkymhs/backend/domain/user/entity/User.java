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

    /*
     * utf8mb4_bin: 대소문자·악센트까지 정확히 같아야 같은 이메일로 본다.
     * MySQL 기본 collation(utf8mb4_0900_ai_ci)은 "User@x.com", "usér@x.com"을 "user@x.com"과 같은 값으로 찾는데,
     * Redis 키(login:fail:{email})는 문자열이 다르면 다른 키다. 둘의 기준이 다르면 이메일 철자를 바꿔가며 로그인 잠금을 피할 수 있다.
     * 그래서 요청에서 이메일을 소문자로 맞추고(DTO), DB는 정확히 비교한다.
     */
    @Column(nullable = false, columnDefinition = "varchar(255) collate utf8mb4_bin")
    private String email;

    // 구글로만 가입한 회원은 비밀번호가 없다 (null)
    private String passwordHash;

    // 구글 계정 고유 ID (id_token의 sub). 구글을 연결하지 않았으면 null
    private String googleSub;

    private String googleEmail;

    // Hibernate는 MySQL에서 enum 필드를 ENUM('DONE',...) 컬럼으로 만든다. 값을 추가하면 기존 DB에 저장이 안 돼서 VARCHAR로 고정한다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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

    // 구글로 처음 가입한 회원. 비밀번호 없이 구글 계정(sub)으로만 로그인한다
    public static User createWithGoogle(String email, String googleSub, String googleEmail) {
        return new User(email, null, googleSub, googleEmail);
    }

    // 기존 이메일 계정에 구글 계정을 연결한다. 이후에는 구글 로그인으로도 이 계정에 들어온다
    public void linkGoogle(String googleSub, String googleEmail) {
        this.googleSub = googleSub;
        this.googleEmail = googleEmail;
    }

    // false면 구글로만 가입한 계정이라 이메일·비밀번호 로그인을 할 수 없다
    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isGoogleLinked() {
        return googleSub != null;
    }
}
