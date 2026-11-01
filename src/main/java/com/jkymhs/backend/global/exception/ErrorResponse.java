package com.jkymhs.backend.global.exception;

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
        return new ErrorResponse(message, errorCode.getStatus().value(), LocalDateTime.now(), description);
    }
}
