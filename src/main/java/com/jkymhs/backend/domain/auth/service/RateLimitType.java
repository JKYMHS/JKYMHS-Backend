package com.jkymhs.backend.domain.auth.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum RateLimitType {
    LOGIN(20, Duration.ofMinutes(1)),
    EMAIL_SEND(5, Duration.ofMinutes(1));

    private final int limit;
    private final Duration window;
}
