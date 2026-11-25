package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.web.CodeEnum;

public enum OpenStatus implements CodeEnum {
    OPEN("open"),
    SOON("soon"),
    CLOSED("closed");

    private final String code;

    OpenStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
