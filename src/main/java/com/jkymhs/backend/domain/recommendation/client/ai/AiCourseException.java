package com.jkymhs.backend.domain.recommendation.client.ai;

public class AiCourseException extends RuntimeException {
    public AiCourseException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiCourseException(String message) {
        super(message);
    }
}
