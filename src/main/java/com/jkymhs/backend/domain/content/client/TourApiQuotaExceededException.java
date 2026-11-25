package com.jkymhs.backend.domain.content.client;

public class TourApiQuotaExceededException extends TourApiException {

    public TourApiQuotaExceededException(String message) {
        super(message);
    }
}
