package com.jkymhs.backend.domain.content.client;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;

public class TourApiException extends RuntimeException {

    public TourApiException(String message) {
        super(message);
    }

    public TourApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomException toApiError() {
        return new CustomException(ErrorCode.EXTERNAL_API_ERROR, "TourAPI 호출 실패");
    }
}
