package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum RecommendationStep implements CodeEnum {
    QUEUED,
    CANDIDATES,
    COMPOSE,
    HOURS,
    ROUTES,
    MEALS;

    public static final int TOTAL = 5;

    @Override
    public String code() {
        return name();
    }

    public int index() {
        return ordinal();
    }
}
