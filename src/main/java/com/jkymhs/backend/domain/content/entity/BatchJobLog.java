package com.jkymhs.backend.domain.content.entity;

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
 * 배치 실행 기록 한 건. 배치가 언제 돌았고 몇 건을 처리했는지, TourAPI를 몇 번 불렀는지 DB에서 바로 볼 수 있게 남긴다.
 * (job_name, started_at) 인덱스는 "이 배치의 최근 실행" 조회용이다.
 */
@Getter
@Entity
@Table(name = "batch_job_logs", indexes = @Index(name = "idx_batch_job_logs_job_name_started_at",
        columnList = "job_name, started_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchJobLog {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "job_name", nullable = false, length = 30)
    private BatchJobName jobName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BatchJobStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "api_call_count", nullable = false)
    private int apiCallCount;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH)
    private String errorMessage;

    private BatchJobLog(BatchJobName jobName, LocalDateTime startedAt) {
        this.jobName = jobName;
        this.status = BatchJobStatus.RUNNING;
        this.startedAt = startedAt;
    }

    public static BatchJobLog start(BatchJobName jobName, LocalDateTime now) {
        return new BatchJobLog(jobName, now);
    }

    public void finish(BatchJobStatus status, int processedCount, int failedCount, int apiCallCount,
                       String errorMessage, LocalDateTime now) {
        this.status = status;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.apiCallCount = apiCallCount;
        this.errorMessage = errorMessage == null || errorMessage.length() <= MAX_ERROR_LENGTH
                ? errorMessage : errorMessage.substring(0, MAX_ERROR_LENGTH);
        this.finishedAt = now;
    }
}
