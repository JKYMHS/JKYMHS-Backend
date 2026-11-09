package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalTime;

/**
 * 코스 사이에 넣는 식사 시간. 시작 시각이 [earliest, latest] 안에 들어올 때만 넣고, 길이는 60분이다.
 * 점심 11:30~13:30, 저녁 17:30~19:30 창 안에서 끝나야 하므로 가장 늦은 시작은 창 끝 − 60분이다.
 */
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
