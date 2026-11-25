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

@Getter
@Entity
@Table(name = "google_calendar_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_google_calendar_links_user_id", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleCalendarLink extends BaseTimeEntity {
    public static final String PRIMARY_CALENDAR = "primary";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_google_calendar_links_user_id"))
    private User user;

    @Column(nullable = false)
    private String googleEmail;

    @Column(nullable = false, columnDefinition = "text")
    private String encryptedRefreshToken;

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

    public void revoke() {
        this.status = CalendarLinkStatus.REVOKED;
    }

    public boolean isActive() {
        return status == CalendarLinkStatus.ACTIVE;
    }
}
