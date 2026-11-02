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
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.description = description;
    }
}
