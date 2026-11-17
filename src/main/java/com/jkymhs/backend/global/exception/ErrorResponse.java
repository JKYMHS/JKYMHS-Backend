package com.jkymhs.backend.global.exception;

import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;

/**
 * 모든 에러 응답의 형식. FE는 문구(message)가 아니라 code로 분기한다.
 * code는 {@link ErrorCode}의 이름 그대로다.
 */
public record ErrorResponse(
        String code,
        String message,
        int status,
        LocalDateTime timestamp,
        String description
) {

    public static ErrorResponse of(ErrorCode errorCode, String description) {
        return of(errorCode, errorCode.getMessage(), description);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String description) {
        return of(errorCode, errorCode.getStatus(), message, description);
    }

    // ErrorCode에 따로 정의하지 않은 상태코드(415, 406 등)는 실제 상태코드를 유지하고 code만 가까운 것을 쓴다
    public static ErrorResponse of(ErrorCode errorCode, HttpStatusCode status, String message, String description) {
        return new ErrorResponse(errorCode.name(), message, status.value(), LocalDateTime.now(), description);
    }
}
