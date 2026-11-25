package com.jkymhs.backend.domain.recommendation.client.ai;

public interface AiCourseClient {
    AiCourseResponse compose(AiCourseRequest request);
}
