package com.jkymhs.backend.domain.recommendation.client.ai;

public class MockAiCourseClient implements AiCourseClient {
    private static final String[] TITLES = {"인기 명소 코스", "여유로운 동선 코스", "숨은 명소 코스"};

    @Override
    public AiCourseResponse compose(AiCourseRequest request) {
        return NearestCoursePlanner.plan(request,
                n -> n < TITLES.length ? TITLES[n] : "추천 코스 " + (n + 1),
                c -> (c.category() == null ? "추천 장소" : c.category()) + " · 인기 장소");
    }
}
