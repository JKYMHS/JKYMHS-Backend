package com.jkymhs.backend.domain.calendar.entity;

import com.jkymhs.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "calendar_sync_jobs", indexes = {
        @Index(name = "idx_calendar_sync_jobs_status_next_attempt_at", columnList = "status, next_attempt_at"),
        @Index(name = "idx_calendar_sync_jobs_user_id_status", columnList = "user_id, status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarSyncJob extends BaseTimeEntity {
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Long tripId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CalendarJobType jobType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CalendarJobStatus status;

    private String calendarId;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int succeeded;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(columnDefinition = "text")
    private String encryptedRefreshToken;

    @Column(nullable = false)
    private boolean revokeAfter;

    @Column(length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    private CalendarSyncJob(Long userId, Long tripId, CalendarJobType jobType, String calendarId, int total,
                            LocalDateTime now) {
        this.userId = userId;
        this.tripId = tripId;
        this.jobType = jobType;
        this.calendarId = calendarId;
        this.total = total;
        this.succeeded = 0;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.revokeAfter = false;
        this.status = total == 0 ? CalendarJobStatus.DONE : CalendarJobStatus.PENDING;
    }

    public static CalendarSyncJob create(Long userId, Long tripId, CalendarJobType jobType, String calendarId,
                                         int total, LocalDateTime now) {
        return new CalendarSyncJob(userId, tripId, jobType, calendarId, total, now);
    }

    public static CalendarSyncJob deleteAll(Long userId, int total, String encryptedRefreshToken, LocalDateTime now) {
        CalendarSyncJob job = new CalendarSyncJob(userId, null, CalendarJobType.DELETE_ALL, null, total, now);
        job.encryptedRefreshToken = encryptedRefreshToken;
        job.revokeAfter = true;
        job.status = CalendarJobStatus.PENDING;
        return job;
    }

    public void claim(LocalDateTime leaseUntil) {
        this.status = CalendarJobStatus.RUNNING;
        this.nextAttemptAt = leaseUntil;
    }

    public void release(LocalDateTime at) {
        this.status = CalendarJobStatus.PENDING;
        this.nextAttemptAt = at;
    }

    public void delay(LocalDateTime at, String error) {
        this.lastError = truncate(error);
        release(at);
    }

    public void backoff(LocalDateTime at, String error) {
        this.attemptCount++;
        this.lastError = truncate(error);
        release(at);
    }

    public void finish(CalendarJobStatus status, int succeeded, String error) {
        this.status = status;
        this.succeeded = succeeded;
        if (error != null) {
            this.lastError = truncate(error);
        }
    }

    public boolean isActive() {
        return CalendarJobStatus.ACTIVE.contains(status);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= LAST_ERROR_MAX_LENGTH ? value : value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
