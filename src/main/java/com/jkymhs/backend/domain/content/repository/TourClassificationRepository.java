package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.TourClassification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourClassificationRepository extends JpaRepository<TourClassification, String> {
}
