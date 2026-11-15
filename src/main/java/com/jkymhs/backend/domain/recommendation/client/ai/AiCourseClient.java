package com.jkymhs.backend.domain.recommendation.client.ai;

/**
 * 후보 목록을 받아 코스 3개를 만들어 주는 AI. 구현은 {@code ai.provider}로 고른다 (mock / http).
 */
public interface AiCourseClient {

    /**
     * @throws AiTimeoutException 응답 대기 시간 초과
     * @throws AiCourseException  그 밖의 호출·응답 오류
     */
    AiCourseResponse compose(AiCourseRequest request);
}
