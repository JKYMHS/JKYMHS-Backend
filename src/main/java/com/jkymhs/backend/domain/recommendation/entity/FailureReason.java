package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum FailureReason implements CodeEnum {
    NOT_ENOUGH_CANDIDATES,
    AI_UNAVAILABLE,
    INTERNAL;

    @Override
    public String code() {
        return name();
    }
}
