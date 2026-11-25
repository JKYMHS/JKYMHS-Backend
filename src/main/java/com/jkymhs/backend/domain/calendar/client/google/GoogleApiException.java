package com.jkymhs.backend.domain.calendar.client.google;

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
