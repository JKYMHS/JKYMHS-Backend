package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalDate;

public enum FestivalStatus implements CodeEnum {
    ONGOING,
    UPCOMING,
    ENDED;

    @Override
    public String code() {
        return name();
    }

    public static FestivalStatus of(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null || end == null) {
            return null;
        }
        if (today.isBefore(start)) {
            return UPCOMING;
        }
        if (today.isAfter(end)) {
            return ENDED;
        }
        return ONGOING;
    }
}
