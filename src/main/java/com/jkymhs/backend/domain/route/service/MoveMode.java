package com.jkymhs.backend.domain.route.service;

import com.jkymhs.backend.global.web.CodeEnum;

public enum MoveMode implements CodeEnum {
    WALK("walk"),
    TRANSIT("transit"),
    TAXI("taxi");

    private final String code;

    MoveMode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
