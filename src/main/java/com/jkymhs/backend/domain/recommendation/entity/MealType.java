package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalTime;

public enum MealType implements CodeEnum {
    LUNCH(LocalTime.of(11, 30), LocalTime.of(13, 30)),
    DINNER(LocalTime.of(17, 30), LocalTime.of(19, 30));

    public static final int MINUTES = 60;

    private final LocalTime windowStart;
    private final LocalTime windowEnd;

    MealType(LocalTime windowStart, LocalTime windowEnd) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    @Override
    public String code() {
        return name();
    }

    public LocalTime windowStart() {
        return windowStart;
    }

    public LocalTime windowEnd() {
        return windowEnd;
    }

    public LocalTime latestStart() {
        return windowEnd.minusMinutes(MINUTES);
    }
}
