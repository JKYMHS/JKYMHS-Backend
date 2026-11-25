package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.repository.ContentCount;
import com.jkymhs.backend.domain.behavior.repository.ContentSaveLogRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BehaviorStatsService {

    private final ContentViewRepository contentViewRepository;
    private final ContentSaveLogRepository contentSaveLogRepository;

    public Map<Long, Long> countViewsSince(LocalDate since) {
        return toMap(contentViewRepository.countByContentSince(since));
    }

    public Map<Long, Long> countSavesSince(LocalDateTime since) {
        return toMap(contentSaveLogRepository.countByContentSince(since));
    }

    private static Map<Long, Long> toMap(List<ContentCount> counts) {
        return counts.stream().collect(Collectors.toMap(ContentCount::contentId, ContentCount::count));
    }
}
