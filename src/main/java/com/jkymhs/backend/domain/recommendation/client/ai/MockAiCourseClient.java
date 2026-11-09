package com.jkymhs.backend.domain.recommendation.client.ai;

/**
 * AI 서버 없이 서버 안에서 코스를 만든다 ({@code ai.provider=mock}, 기본값). 항상 같은 입력에 같은 결과를 낸다.
 * AI 담당 팀원의 서버가 준비되기 전에도 FE가 추천 화면 전체를 붙여 볼 수 있게 하려는 것이다.
 */
public class MockAiCourseClient implements AiCourseClient {

    private static final String[] TITLES = {"인기 명소 코스", "여유로운 동선 코스", "숨은 명소 코스"};

    @Override
    public AiCourseResponse compose(AiCourseRequest request) {
        return NearestCoursePlanner.plan(request,
                n -> n < TITLES.length ? TITLES[n] : "추천 코스 " + (n + 1),
                c -> (c.category() == null ? "추천 장소" : c.category()) + " · 인기 장소");
    }
}
