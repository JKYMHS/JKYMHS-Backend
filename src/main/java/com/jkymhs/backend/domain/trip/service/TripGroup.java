package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalDate;

/**
 * 여행 목록의 묶음. 종료일이 오늘 이후(오늘 포함)면 다가오는 여행이다 (여행 중인 것도 다가오는 쪽).
 */
public enum TripGroup implements CodeEnum {
    UPCOMING("upcoming"),
    PAST("past");

    private final String code;

    TripGroup(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static TripGroup of(LocalDate endDate, LocalDate today) {
        return endDate.isBefore(today) ? PAST : UPCOMING;
    }
}
