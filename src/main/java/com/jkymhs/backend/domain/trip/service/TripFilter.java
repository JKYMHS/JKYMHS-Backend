package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 여행 목록 필터. unconfirmed = 확정 일정안이 없는 여행 (날짜 무관).
 */
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
