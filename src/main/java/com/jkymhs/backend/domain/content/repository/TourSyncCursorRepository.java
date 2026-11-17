package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.TourSyncCursor;
import com.jkymhs.backend.domain.content.entity.TourSyncCursorName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourSyncCursorRepository extends JpaRepository<TourSyncCursor, TourSyncCursorName> {
}
