package com.jkymhs.backend.domain.recommendation.repository;

import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequiredPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface RecommendationRequiredPlaceRepository extends JpaRepository<RecommendationRequiredPlace, Long> {

    List<RecommendationRequiredPlace> findAllByRequestIdOrderBySortOrderAsc(Long requestId);

    @Modifying
    @Query("delete from RecommendationRequiredPlace p where p.requestId in :requestIds")
    int deleteAllByRequestIdIn(Collection<Long> requestIds);
}
