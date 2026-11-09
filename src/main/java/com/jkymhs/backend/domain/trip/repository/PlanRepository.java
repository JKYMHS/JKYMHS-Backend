package com.jkymhs.backend.domain.trip.repository;

import com.jkymhs.backend.domain.trip.entity.Plan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    // 전체 저장·장소 담기: 같은 일정안을 두 기기가 동시에 저장하면 한쪽이 기다렸다가 버전 비교에서 409를 받는다 (G10)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plan p where p.id = :id")
    Optional<Plan> findByIdForUpdate(Long id);

    List<Plan> findAllByTripIdOrderByIdAsc(Long tripId);

    List<Plan> findAllByTripIdInOrderByIdAsc(Collection<Long> tripIds);

    long countByTripId(Long tripId);

    // [planId, tripId] (탈퇴 시 엔티티를 불러오지 않고 일정안 → 여행을 찾는다)
    @Query("select p.id, p.tripId from Plan p where p.tripId in :tripIds")
    List<Object[]> findIdAndTripIdByTripIdIn(Collection<Long> tripIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from Plan p where p.id = :id")
    int deleteByIdInBulk(Long id);

    @Query("select count(p) from Plan p where p.tripId in (select t.id from Trip t where t.userId = :userId)")
    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Plan p where p.tripId in :tripIds")
    int deleteAllByTripIdIn(Collection<Long> tripIds);
}
