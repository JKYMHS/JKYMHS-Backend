package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseRequest;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.recommendation.client.ai.NearestCoursePlanner;
import org.springframework.stereotype.Component;

@Component
public class FallbackCourseBuilder {
    public static final String TITLE = "기본 추천 코스";

    public AiCourseResponse build(AiCourseRequest request) {
        return NearestCoursePlanner.plan(request, n -> TITLE,
                c -> (c.category() == null ? "추천 장소" : c.category()) + " · 가까운 순 추천");
    }
}
