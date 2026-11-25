package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum VisitCheckStatus implements CodeEnum {
    PENDING,
    DISMISSED,
    DONE;

    @Override
    public String code() {
        return name();
    }
}
