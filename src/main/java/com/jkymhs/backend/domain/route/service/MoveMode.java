package com.jkymhs.backend.domain.route.service;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 장소와 장소 사이 이동 수단. FE 값은 소문자다. DB(plan_items.transport_mode)에는 대문자 name()이 저장된다.
 */
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
