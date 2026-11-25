package com.jkymhs.backend.domain.recommendation.repository;

import com.jkymhs.backend.domain.recommendation.entity.FailureReason;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {
    Optional<RecommendationRequest> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndRequestDateAndStatusNot(Long userId, LocalDate requestDate, RecommendationStatus status);

    Optional<RecommendationRequest> findFirstByUserIdAndStatusAndConditionsKeyAndRequestedAtAfterOrderByIdDesc(
            Long userId, RecommendationStatus status, String conditionsKey, LocalDateTime after);

    @Query("select r.id from RecommendationRequest r where r.userId = :userId")
    List<Long> findIdsByUserId(Long userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update RecommendationRequest r set r.status = :status, r.failureReason = :reason
            where r.id = :id and r.status = com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus.PENDING
            """)
    int finish(Long id, RecommendationStatus status, FailureReason reason);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update RecommendationRequest r
            set r.status = com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus.FAILED,
                r.failureReason = com.jkymhs.backend.domain.recommendation.entity.FailureReason.INTERNAL
            where r.status = com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus.PENDING
              and r.requestedAt < :before
            """)
    int failStalePending(LocalDateTime before);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update RecommendationRequest r set r.originLat = null, r.originLng = null, r.originAddress = null
            where r.requestedAt < :before and (r.originLat is not null or r.originAddress is not null)
            """)
    int clearOriginsBefore(LocalDateTime before);

    @Modifying
    @Query("delete from RecommendationRequest r where r.userId = :userId")
    int deleteAllByUserId(Long userId);
}
