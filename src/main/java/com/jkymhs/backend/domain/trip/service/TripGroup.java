package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalDate;

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
