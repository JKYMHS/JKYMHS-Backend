package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum PlanSource implements CodeEnum {
    AI,
    AI_RETRY,
    MANUAL;

    @Override
    public String code() {
        return name();
    }
}
