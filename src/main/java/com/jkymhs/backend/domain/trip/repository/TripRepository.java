package com.jkymhs.backend.domain.trip.repository;

import com.jkymhs.backend.domain.trip.entity.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    // 여행 단위 쓰기는 여행 행을 먼저 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(Long id);

    @Query("""
            select t from Trip t
            where t.userId = :userId and t.endDate >= :today
              and (:unconfirmedOnly = false or t.confirmedPlanId is null)
              and (:afterDate is null or t.startDate > :afterDate or (t.startDate = :afterDate and t.id > :afterId))
            order by t.startDate asc, t.id asc
            """)
    List<Trip> findUpcomingPage(Long userId, LocalDate today, boolean unconfirmedOnly, LocalDate afterDate,
                                Long afterId, Pageable pageable);

    @Query("""
            select t from Trip t
            where t.userId = :userId and t.endDate < :today
              and (:unconfirmedOnly = false or t.confirmedPlanId is null)
              and (:afterDate is null or t.startDate < :afterDate or (t.startDate = :afterDate and t.id < :afterId))
            order by t.startDate desc, t.id desc
            """)
    List<Trip> findPastPage(Long userId, LocalDate today, boolean unconfirmedOnly, LocalDate afterDate,
                            Long afterId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndEndDateGreaterThanEqual(Long userId, LocalDate today);

    long countByUserIdAndEndDateBefore(Long userId, LocalDate today);

    long countByUserIdAndConfirmedPlanIdIsNull(Long userId);

    List<Trip> findAllByUserIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(Long userId, LocalDate today);

    @Query("""
            select t from Trip t
            where t.userId = :userId and t.endDate < :today and t.confirmedPlanId is not null
              and (t.visitCheckStatus is null
                   or t.visitCheckStatus = com.jkymhs.backend.domain.trip.entity.VisitCheckStatus.PENDING)
              and (t.visitCheckNextAt is null or t.visitCheckNextAt <= :today)
            order by t.endDate desc, t.id desc
            """)
    List<Trip> findVisitCheckCandidates(Long userId, LocalDate today);

    @Query("""
            select t from Trip t
            where t.endDate < :today and t.endDate >= :since and t.confirmedPlanId is not null
              and t.visitEmailSentAt is null
              and (t.visitCheckStatus is null
                   or t.visitCheckStatus = com.jkymhs.backend.domain.trip.entity.VisitCheckStatus.PENDING)
              and (t.visitCheckNextAt is null or t.visitCheckNextAt <= :today)
            order by t.id asc
            """)
    List<Trip> findVisitMailCandidates(LocalDate today, LocalDate since);

    @Query("select t.id from Trip t where t.userId = :userId")
    List<Long> findIdsByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Trip t where t.id = :id")
    int deleteByIdInBulk(Long id);

    @Modifying(flushAutomatically = true)
    @Query("delete from Trip t where t.userId = :userId")
    int deleteAllByUserId(Long userId);
}
