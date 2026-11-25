package com.jkymhs.backend.domain.calendar.client.google;

public class GoogleAuthExpiredException extends RuntimeException {
    public GoogleAuthExpiredException(String message) {
        super(message);
    }
}
