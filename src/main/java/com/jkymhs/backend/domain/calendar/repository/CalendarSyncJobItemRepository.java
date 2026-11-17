package com.jkymhs.backend.domain.calendar.repository;

import com.jkymhs.backend.domain.calendar.entity.CalendarJobItemStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJobItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarSyncJobItemRepository extends JpaRepository<CalendarSyncJobItem, Long> {

    List<CalendarSyncJobItem> findAllByJobIdOrderByIdAsc(Long jobId);

    // 작업자 한 차례 분량 (batch-size개)
    List<CalendarSyncJobItem> findAllByJobIdAndStatusOrderByIdAsc(Long jobId, CalendarJobItemStatus status, Pageable pageable);

    List<CalendarSyncJobItem> findAllByJobIdAndStatus(Long jobId, CalendarJobItemStatus status);

    long countByJobIdAndStatus(Long jobId, CalendarJobItemStatus status);
}
