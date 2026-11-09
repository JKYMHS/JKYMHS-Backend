package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 진행 화면에 보여줄 단계. QUEUED(0) 다음 다섯 단계가 1~5다 (stepTotal = 5).
 */
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
