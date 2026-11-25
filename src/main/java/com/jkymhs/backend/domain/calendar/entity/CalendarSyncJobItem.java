package com.jkymhs.backend.domain.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "calendar_sync_job_items", indexes = @Index(name = "idx_calendar_sync_job_items_job_id_status",
        columnList = "job_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarSyncJobItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_calendar_sync_job_items_job_id"))
    private CalendarSyncJob job;

    private Long planItemId;

    private Long tripId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CalendarJobOperation operation;

    @Column(nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false)
    private String calendarId;

    @Column(length = 200)
    private String itemName;

    @Column(length = 30)
    private String startLabel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CalendarJobItemStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private CalendarFailureReason reasonCode;

    @Column(nullable = false)
    private int attemptCount;

    private CalendarSyncJobItem(Long jobId, Long planItemId, Long tripId, CalendarJobOperation operation,
                                String eventId, String calendarId, String itemName, String startLabel) {
        this.jobId = jobId;
        this.planItemId = planItemId;
        this.tripId = tripId;
        this.operation = operation;
        this.eventId = eventId;
        this.calendarId = calendarId;
        this.itemName = itemName;
        this.startLabel = startLabel;
        this.status = CalendarJobItemStatus.PENDING;
        this.attemptCount = 0;
    }

    public static CalendarSyncJobItem create(Long jobId, Long planItemId, Long tripId, CalendarJobOperation operation,
                                             String eventId, String calendarId, String itemName, String startLabel) {
        return new CalendarSyncJobItem(jobId, planItemId, tripId, operation, eventId, calendarId, itemName, startLabel);
    }

    public void done() {
        this.status = CalendarJobItemStatus.DONE;
        this.reasonCode = null;
    }

    public void fail(CalendarFailureReason reason) {
        this.status = CalendarJobItemStatus.FAILED;
        this.reasonCode = reason;
    }

    public boolean retryLater(int maxAttempts) {
        this.attemptCount++;
        if (attemptCount >= maxAttempts) {
            fail(CalendarFailureReason.GOOGLE_ERROR);
            return false;
        }
        return true;
    }
}
