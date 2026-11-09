package com.jkymhs.backend.domain.recommendation.client.ai;

/**
 * AI 서버 호출 실패. 추천 작업은 이 예외를 받으면 기본 추천 코스로 대신한다 (사용자에게 에러로 보이지 않는다).
 */
public class AiCourseException extends RuntimeException {

    public AiCourseException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiCourseException(String message) {
        super(message);
    }
}
