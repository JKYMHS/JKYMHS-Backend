package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.global.web.CodeEnum;

public enum TripFilter implements CodeEnum {
    ALL("all"),
    UPCOMING("upcoming"),
    PAST("past"),
    UNCONFIRMED("unconfirmed");

    private final String code;

    TripFilter(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
