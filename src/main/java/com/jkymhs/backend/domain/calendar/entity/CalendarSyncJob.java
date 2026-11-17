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

/**
 * 구글 캘린더 작업 하나 (업로드·자동 동기화·삭제). 요청 스레드에서 구글을 부르지 않고 이 행만 넣은 뒤 202를 준다.
 * 실제 호출은 {@code CalendarSyncWorker}가 2초마다 PENDING 작업을 집어서 한다 (FE는 GET /api/calendar/jobs/{id}로 진행을 본다).
 *
 * 요청에서 바로 부르지 않는 이유: 여행 하나에 일정이 30개면 구글 호출이 30번이고, 429·5xx면 기다렸다 다시 해야 한다.
 * 요청 스레드가 그동안 붙잡혀 있으면 안 되고, 서버가 재시작돼도 작업이 사라지면 안 되어서 DB에 남긴다.
 *
 * user_id·trip_id에 FK를 걸지 않는다: 탈퇴하면 회원·여행 행은 지워지지만, 그 회원의 일정을 구글에서 지우는 작업(DELETE_ALL)은
 * 그 뒤에도 돌아야 하기 때문이다.
 *
 * 인덱스: 작업자가 "PENDING이고 차례가 된 작업"을 찾는 (status, next_attempt_at), "이 회원의 진행 중 작업"(409 확인)을 찾는 (user_id, status).
 */
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

    // 여행 하나에 대한 작업이면 그 여행. 여러 여행(BULK_UPLOAD)·전체 삭제(DELETE_ALL)는 null
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CalendarJobType jobType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CalendarJobStatus status;

    // 올릴 캘린더 (업로드). 항목마다 calendar_id가 따로 있어서 작업 단위로는 참고값이다
    private String calendarId;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int succeeded;

    // 작업 단위로 다시 미룬 횟수 (429·5xx·토큰 발급 실패). 백오프 간격 계산에 쓴다
    @Column(nullable = false)
    private int attemptCount;

    // 이 시각이 지나야 작업자가 집는다. RUNNING일 때는 "작업자가 죽었다고 보고 다시 집을 시각"(임대 만료)이다
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    // DELETE_ALL만: 연동 행이 먼저 지워져도(탈퇴) 구글에 접근할 수 있게 넣어 둔 암호화된 리프레시 토큰
    @Column(columnDefinition = "text")
    private String encryptedRefreshToken;

    // true면 작업이 끝난 뒤 토큰을 구글에서 폐기(revoke)하고 연동 행을 지운다 (해제·탈퇴)
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
        // 할 일이 없는 작업(일괄 업로드에서 모든 여행이 건너뛰어짐 등)은 바로 끝난 것으로 만든다
        this.status = total == 0 ? CalendarJobStatus.DONE : CalendarJobStatus.PENDING;
    }

    public static CalendarSyncJob create(Long userId, Long tripId, CalendarJobType jobType, String calendarId,
                                         int total, LocalDateTime now) {
        return new CalendarSyncJob(userId, tripId, jobType, calendarId, total, now);
    }

    /** 해제·탈퇴: 토큰 사본을 넣고, 끝나면 폐기하도록 표시한다. 지울 일정이 없어도 토큰 폐기는 해야 해서 PENDING으로 둔다 */
    public static CalendarSyncJob deleteAll(Long userId, int total, String encryptedRefreshToken, LocalDateTime now) {
        CalendarSyncJob job = new CalendarSyncJob(userId, null, CalendarJobType.DELETE_ALL, null, total, now);
        job.encryptedRefreshToken = encryptedRefreshToken;
        job.revokeAfter = true;
        job.status = CalendarJobStatus.PENDING;
        return job;
    }

    /** 작업자가 집었다. 작업자가 중간에 죽으면 leaseUntil이 지난 뒤 다른 작업자가 다시 집는다 */
    public void claim(LocalDateTime leaseUntil) {
        this.status = CalendarJobStatus.RUNNING;
        this.nextAttemptAt = leaseUntil;
    }

    /** 이번 차례에 다 못 끝냈다. at부터 다시 집는다 */
    public void release(LocalDateTime at) {
        this.status = CalendarJobStatus.PENDING;
        this.nextAttemptAt = at;
    }

    /** 항목 하나가 429·5xx를 받았다. 작업 횟수는 그대로 두고(항목이 자기 횟수를 센다) at까지 미룬다 */
    public void delay(LocalDateTime at, String error) {
        this.lastError = truncate(error);
        release(at);
    }

    /** 작업 단위로 실패했다(토큰 발급 5xx 등). 횟수를 올리고 at까지 미룬다 */
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
