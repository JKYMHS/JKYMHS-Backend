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

    /**
     * 같은 에러 코드라도 상황마다 사용자에게 보여줄 문구가 다를 때 쓴다.
     * 예: SCHEDULE_LIMIT_EXCEEDED는 code는 같고 "하루에는 장소를 최대 10개까지 넣을 수 있습니다."처럼 문구만 바꾼다.
     * FE는 code로 분기하고 message는 그대로 화면에 띄우므로, code를 새로 만들지 않고 문구만 바꾸면 된다.
     */
    public CustomException(ErrorCode errorCode, String message, String description) {
        super(message);
        this.errorCode = errorCode;
        this.description = description;
    }
}
