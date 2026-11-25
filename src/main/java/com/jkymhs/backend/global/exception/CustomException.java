package com.jkymhs.backend.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String description;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public CustomException(ErrorCode errorCode, String description) {
        this(errorCode, errorCode.getMessage(), description);
    }

    public CustomException(ErrorCode errorCode, String message, String description) {
        super(message);
        this.errorCode = errorCode;
        this.description = description;
    }
}
