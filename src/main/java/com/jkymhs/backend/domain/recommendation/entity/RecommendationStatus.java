package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum RecommendationStatus implements CodeEnum {
    PENDING,
    DONE,
    FALLBACK,
    FAILED;

    @Override
    public String code() {
        return name();
    }

    public boolean hasResult() {
        return this == DONE || this == FALLBACK;
    }
}
