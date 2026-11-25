package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@RequiredArgsConstructor
public enum TimeOfDay implements CodeEnum {

    EARLY("early", LocalTime.of(9, 0)),
    MORNING("morning", LocalTime.of(10, 0)),
    AFTERNOON("afternoon", LocalTime.of(13, 0));

    private final String code;
    private final LocalTime startTime;

    @Override
    public String code() {
        return code;
    }

    public LocalTime startTime() {
        return startTime;
    }
}
