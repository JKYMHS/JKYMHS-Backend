package com.jkymhs.backend.global.exception;

import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        int status,
        LocalDateTime timestamp,
        String description
) {

    public static ErrorResponse of(ErrorCode errorCode, String description) {
        return of(errorCode, errorCode.getMessage(), description);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String description) {
        return of(errorCode.getStatus(), message, description);
    }

    public static ErrorResponse of(HttpStatusCode status, String message, String description) {
        return new ErrorResponse(message, status.value(), LocalDateTime.now(), description);
    }
}
