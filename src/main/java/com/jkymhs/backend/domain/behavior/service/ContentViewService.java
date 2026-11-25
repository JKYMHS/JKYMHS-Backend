package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentViewService {

    private final ContentViewRepository contentViewRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public boolean recordView(Long userId, Long contentId) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        boolean first = contentViewRepository.insertIgnore(userId, contentId, now.toLocalDate(), now) == 1;
        if (first) {
            eventPublisher.publishEvent(
                    new BehaviorSignalEvent(userId, contentId, BehaviorSignalType.VIEW, null, null, now));
        }
        return first;
    }
}
