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

    /*
     * 작업자가 처리할 작업을 집는다. 차례가 된 PENDING 작업과, 임대 시간이 지난 RUNNING 작업(작업자가 처리 중에 죽은 것)이다.
     * FOR UPDATE SKIP LOCKED: 서버가 여러 대일 때 다른 서버가 이미 잠근 행은 기다리지 않고 건너뛴다.
     * 같은 작업을 두 서버가 동시에 처리하지 않고, 서로 기다리지도 않는다. JPQL에는 SKIP LOCKED가 없어서 네이티브 쿼리를 쓴다.
     */
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
