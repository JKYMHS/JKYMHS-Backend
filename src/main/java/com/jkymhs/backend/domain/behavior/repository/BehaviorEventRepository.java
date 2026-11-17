package com.jkymhs.backend.domain.behavior.repository;

import com.jkymhs.backend.domain.behavior.entity.BehaviorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {

    // idx_behavior_events_user_id_occurred_at을 탄다
    List<BehaviorEvent> findAllByUserIdAndOccurredAtGreaterThanEqual(Long userId, LocalDateTime since);

    List<BehaviorEvent> findAllByUserIdAndCompanionAndOccurredAtGreaterThanEqual(Long userId, String companion,
                                                                                 LocalDateTime since);

    @Modifying
    @Query("delete from BehaviorEvent e where e.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
