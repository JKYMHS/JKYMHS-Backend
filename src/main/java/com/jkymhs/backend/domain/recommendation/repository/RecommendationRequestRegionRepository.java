package com.jkymhs.backend.domain.recommendation.repository;

import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequestRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface RecommendationRequestRegionRepository extends JpaRepository<RecommendationRequestRegion, Long> {

    List<RecommendationRequestRegion> findAllByRequestIdOrderByIdAsc(Long requestId);

    @Modifying
    @Query("delete from RecommendationRequestRegion r where r.requestId in :requestIds")
    int deleteAllByRequestIdIn(Collection<Long> requestIds);
}
