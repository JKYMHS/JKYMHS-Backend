package com.jkymhs.backend.domain.content.client;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;

/**
 * TourAPI 호출 실패 (재시도 후에도 5xx·네트워크 오류, 오류 응답 코드, 읽을 수 없는 응답).
 * 배치는 이 예외를 잡아서 항목 실패로 센다. API 요청 처리 중(D1 상세 갱신)이면 {@link #toApiError()}로 502를 준다.
 */
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
