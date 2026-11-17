package com.jkymhs.backend.domain.trip.repository;

import com.jkymhs.backend.domain.trip.entity.TripRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TripRegionRepository extends JpaRepository<TripRegion, Long> {

    List<TripRegion> findAllByTripIdOrderByIdAsc(Long tripId);

    List<TripRegion> findAllByTripIdInOrderByIdAsc(Collection<Long> tripIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from TripRegion r where r.tripId in :tripIds")
    int deleteAllByTripIdIn(Collection<Long> tripIds);
}
