package com.jkymhs.backend.domain.calendar.client.google;

/**
 * 구글 캘린더 API가 실패했다. status 0은 응답을 못 받은 경우(타임아웃·네트워크 오류)다.
 * 작업자가 이 값을 보고 "다시 시도(429·5xx·0)", "쓰기 권한 없음(403)", "그 밖의 실패"로 나눈다.
 */
public class GoogleApiException extends RuntimeException {

    private final int status;
    private final String responseBody;

    public GoogleApiException(int status, String responseBody, String message) {
        super(message);
        this.status = status;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public int status() {
        return status;
    }

    public boolean notFound() {
        return status == 404 || status == 410;
    }

    public boolean conflict() {
        return status == 409;
    }

    /**
     * 잠시 뒤 다시 하면 될 수 있는 오류. 구글은 사용량 초과를 429뿐 아니라 403(rateLimitExceeded)으로도 준다.
     */
    public boolean retryable() {
        return status == 0 || status == 429 || status >= 500 || (status == 403 && rateLimited());
    }

    public boolean forbidden() {
        return status == 403 && !rateLimited();
    }

    private boolean rateLimited() {
        return responseBody.contains("rateLimitExceeded") || responseBody.contains("userRateLimitExceeded");
    }
}
