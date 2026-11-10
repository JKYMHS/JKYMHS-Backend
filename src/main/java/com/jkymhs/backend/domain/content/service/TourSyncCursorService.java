package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.TourSyncCursor;
import com.jkymhs.backend.domain.content.entity.TourSyncCursorName;
import com.jkymhs.backend.domain.content.repository.TourSyncCursorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 동기화 배치 진행 위치 읽기·쓰기.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TourSyncCursorService {

    private final TourSyncCursorRepository tourSyncCursorRepository;
    private final Clock clock;

    public Optional<String> get(TourSyncCursorName name) {
        return tourSyncCursorRepository.findById(name).map(TourSyncCursor::getValue);
    }

    @Transactional
    public void set(TourSyncCursorName name, String value) {
        LocalDateTime now = LocalDateTime.now(clock);
        tourSyncCursorRepository.findById(name).ifPresentOrElse(
                cursor -> cursor.moveTo(value, now),
                () -> tourSyncCursorRepository.save(TourSyncCursor.create(name, value, now)));
    }
}
