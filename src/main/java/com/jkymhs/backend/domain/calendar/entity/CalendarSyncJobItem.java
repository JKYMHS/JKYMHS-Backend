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

/**
 * 작업 안의 일정 하나 (구글 호출 한 묶음). 작업 상태 화면의 "실패한 항목" 목록과 "실패만 다시"가 이 행을 읽는다.
 *
 * plan_item_id·trip_id에 FK를 걸지 않는다: 삭제 작업은 일정 항목·여행이 지워진 뒤에 돈다.
 * item_name·start_label은 작업을 만들 때 복사해 둔다 (항목이 지워져도 "무엇이 실패했는지" 보여줄 수 있게).
 * 생성·수정 시각 컬럼이 없어서 BaseTimeEntity를 상속하지 않는다 (작업 행의 시각으로 충분하다).
 */
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

    // CREATE·UPDATE: 올릴 캘린더, MOVE: 옮겨 갈 캘린더, DELETE: 일정이 있는 캘린더
    @Column(nullable = false)
    private String calendarId;

    @Column(length = 200)
    private String itemName;

    // "11.10 09:00" / "11.10 종일" (실패 목록에 보여줄 때)
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

    /**
     * 429·5xx·네트워크 오류. 횟수를 올리고, 최대 횟수에 닿았으면 실패로 끝낸다.
     *
     * @return 다시 시도할 수 있으면 true
     */
    public boolean retryLater(int maxAttempts) {
        this.attemptCount++;
        if (attemptCount >= maxAttempts) {
            fail(CalendarFailureReason.GOOGLE_ERROR);
            return false;
        }
        return true;
    }
}
