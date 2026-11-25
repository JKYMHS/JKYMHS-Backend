package com.jkymhs.backend.domain.calendar.repository;

import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarSyncJobRepository extends JpaRepository<CalendarSyncJob, Long> {
    @Query(value = """
            SELECT * FROM calendar_sync_jobs
            WHERE status IN ('PENDING', 'RUNNING') AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CalendarSyncJob> findDueForUpdate(LocalDateTime now, int limit);

    Optional<CalendarSyncJob> findFirstByUserIdAndJobTypeInAndStatusInOrderByIdAsc(
            Long userId, Collection<CalendarJobType> types, Collection<CalendarJobStatus> statuses);

    boolean existsByTripIdAndStatusIn(Long tripId, Collection<CalendarJobStatus> statuses);
}
