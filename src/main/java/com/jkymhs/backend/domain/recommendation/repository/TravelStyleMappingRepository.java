package com.jkymhs.backend.domain.recommendation.repository;

import com.jkymhs.backend.domain.recommendation.entity.TravelStyleMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelStyleMappingRepository extends JpaRepository<TravelStyleMapping, Long> {
}
