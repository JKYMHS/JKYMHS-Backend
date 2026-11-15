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

/**
 * 인기 점수 배치가 쓰는 콘텐츠별 집계. 배치(popularity 패키지)가 행동 데이터 테이블을 직접 읽지 않게 이 서비스로 연다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BehaviorStatsService {

    private final ContentViewRepository contentViewRepository;
    private final ContentSaveLogRepository contentSaveLogRepository;

    /** since 날짜(포함)부터의 상세 조회 수 (회원·날짜당 1회) */
    public Map<Long, Long> countViewsSince(LocalDate since) {
        return toMap(contentViewRepository.countByContentSince(since));
    }

    /** since 시각(포함)부터 일정에 담긴 수 */
    public Map<Long, Long> countSavesSince(LocalDateTime since) {
        return toMap(contentSaveLogRepository.countByContentSince(since));
    }

    private static Map<Long, Long> toMap(List<ContentCount> counts) {
        return counts.stream().collect(Collectors.toMap(ContentCount::contentId, ContentCount::count));
    }
}
