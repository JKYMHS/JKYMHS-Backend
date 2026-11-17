package com.jkymhs.backend.domain.recommendation.client.ai;

public class AiTimeoutException extends AiCourseException {

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
