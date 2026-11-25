package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Pace implements CodeEnum {

    RELAXED("relaxed"),
    PACKED("packed");

    private final String code;

    @Override
    public String code() {
        return code;
    }
}
