package com.jkymhs.backend.domain.calendar.entity;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

import java.time.LocalDateTime;

/**
 * 회원 한 명의 구글 캘린더 연동 (회원당 하나, UK user_id).
 *
 * 구글 로그인(users.google_sub)과 따로 둔다. 로그인은 "누구인지"만 확인하면 되지만, 캘린더는 서버가 사용자가 없을 때도
 * (자동 동기화·탈퇴 정리) 일정을 써야 해서 오래 쓰는 리프레시 토큰과 캘린더 권한(scope)이 필요하다.
 * 이메일·비밀번호로 가입한 회원도 캘린더만 연동할 수 있다.
 *
 * 리프레시 토큰은 평문으로 저장하지 않는다 (AES-256-GCM, {@code CalendarTokenCipher}). DB가 유출돼도 키 없이는 구글에 접근할 수 없다.
 */
@Getter
@Entity
@Table(name = "google_calendar_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_google_calendar_links_user_id", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleCalendarLink extends BaseTimeEntity {

    // 처음 연동하면 구글이 "primary"라는 별칭으로 부르는 기본 캘린더에 올린다
    public static final String PRIMARY_CALENDAR = "primary";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // FK 제약만 만든다 (회원 id는 위 필드로 읽는다)
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_google_calendar_links_user_id"))
    private User user;

    @Column(nullable = false)
    private String googleEmail;

    @Column(nullable = false, columnDefinition = "text")
    private String encryptedRefreshToken;

    // 사용자가 동의 화면에서 실제로 허용한 권한 (공백으로 구분). 권한이 빠졌는지 나중에 확인할 수 있게 남긴다
    @Column(nullable = false, length = 500)
    private String grantedScopes;

    @Column(nullable = false)
    private String defaultCalendarId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CalendarLinkStatus status;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    private GoogleCalendarLink(Long userId, String googleEmail, String encryptedRefreshToken, String grantedScopes,
                               LocalDateTime linkedAt) {
        this.userId = userId;
        this.googleEmail = googleEmail;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.grantedScopes = grantedScopes;
        this.defaultCalendarId = PRIMARY_CALENDAR;
        this.status = CalendarLinkStatus.ACTIVE;
        this.linkedAt = linkedAt;
    }

    public static GoogleCalendarLink create(Long userId, String googleEmail, String encryptedRefreshToken,
                                            String grantedScopes, LocalDateTime now) {
        return new GoogleCalendarLink(userId, googleEmail, encryptedRefreshToken, grantedScopes, now);
    }

    /**
     * 다시 연동: 새 토큰으로 바꾸고 ACTIVE로 되돌린다. 다른 구글 계정으로 바꿨으면 예전 계정의 캘린더 id는 쓸 수 없어서
     * 기본 캘린더를 primary로 되돌린다.
     */
    public void relink(String googleEmail, String encryptedRefreshToken, String grantedScopes, LocalDateTime now) {
        if (!this.googleEmail.equalsIgnoreCase(googleEmail)) {
            this.defaultCalendarId = PRIMARY_CALENDAR;
        }
        this.googleEmail = googleEmail;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.grantedScopes = grantedScopes;
        this.status = CalendarLinkStatus.ACTIVE;
        this.linkedAt = now;
    }

    public void changeDefaultCalendar(String calendarId) {
        this.defaultCalendarId = calendarId;
    }

    /** 구글이 리프레시 토큰을 거부했다 (invalid_grant) */
    public void revoke() {
        this.status = CalendarLinkStatus.REVOKED;
    }

    public boolean isActive() {
        return status == CalendarLinkStatus.ACTIVE;
    }
}
