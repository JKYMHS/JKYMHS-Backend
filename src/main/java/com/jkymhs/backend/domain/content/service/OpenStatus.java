package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 지금 영업 중인지. FE 값은 소문자다 (open 영업 중 · soon 곧 마감 · closed 영업 종료·휴무).
 */
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
