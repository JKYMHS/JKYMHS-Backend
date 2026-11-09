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

    // 일정안 추가·삭제·확정, 여행 수정처럼 여행 단위로 순서가 중요한 쓰기는 여행 행을 먼저 잠근다 (G10)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(Long id);

    /*
     * 여행 목록 커서 조회. "다가오는 여행"(종료일 ≥ 오늘)은 시작일 → id 오름차순, "지난 여행"은 시작일 → id 내림차순이다.
     * afterDate/afterId가 null이면 처음부터, 있으면 (시작일, id)가 커서보다 뒤인 것만 읽는다.
     * unconfirmedOnly=true면 확정 일정안이 없는 여행만 (필터 unconfirmed).
     */
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

    // 담기 대상 목록: 다가오는 여행만 시작일 순
    List<Trip> findAllByUserIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(Long userId, LocalDate today);

    // 방문 확인 후보: 끝났고 확정 일정안이 있고, 닫히지 않았고, "나중에"로 미룬 날이 지난 여행. 가장 최근에 끝난 것부터
    @Query("""
            select t from Trip t
            where t.userId = :userId and t.endDate < :today and t.confirmedPlanId is not null
              and (t.visitCheckStatus is null
                   or t.visitCheckStatus = com.jkymhs.backend.domain.trip.entity.VisitCheckStatus.PENDING)
              and (t.visitCheckNextAt is null or t.visitCheckNextAt <= :today)
            order by t.endDate desc, t.id desc
            """)
    List<Trip> findVisitCheckCandidates(Long userId, LocalDate today);

    // 방문 확인 메일 후보 (전체 회원). 메일을 보낸 적 없는 여행만
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

    List<Trip> findAllByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Trip t where t.userId = :userId")
    int deleteAllByUserId(Long userId);
}
