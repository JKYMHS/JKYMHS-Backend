package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseRequest;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.recommendation.client.ai.NearestCoursePlanner;
import org.springframework.stereotype.Component;

/**
 * AI가 실패하거나(오류·시간 초과) 두 번 다 규칙을 어기면 서버가 직접 만드는 "기본 추천 코스".
 * 점수 높은 후보를 출발지에서 가까운 순으로 이어 붙이고, 시작 후보를 밀어서 3가지를 만든다.
 * AI 없이 만든 코스라 결과 상태는 FALLBACK이고 FE가 "기본 추천 코스" 라벨을 붙인다.
 */
@Component
public class FallbackCourseBuilder {

    public static final String TITLE = "기본 추천 코스";

    public AiCourseResponse build(AiCourseRequest request) {
        return NearestCoursePlanner.plan(request, n -> TITLE,
                c -> (c.category() == null ? "추천 장소" : c.category()) + " · 가까운 순 추천");
    }
}
