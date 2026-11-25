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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plan p where p.id = :id")
    Optional<Plan> findByIdForUpdate(Long id);

    List<Plan> findAllByTripIdOrderByIdAsc(Long tripId);

    List<Plan> findAllByTripIdInOrderByIdAsc(Collection<Long> tripIds);

    long countByTripId(Long tripId);

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
