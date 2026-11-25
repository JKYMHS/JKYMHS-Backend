package com.jkymhs.backend.domain.content.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum ContentType implements CodeEnum {
    ATTRACTION("attraction"),
    FESTIVAL("festival"),
    RESTAURANT("restaurant"),
    LODGING("lodging");

    private final String code;

    ContentType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static ContentType ofTourTypeId(int tourContentTypeId) {
        return switch (tourContentTypeId) {
            case 12, 14 -> ATTRACTION;
            case 15 -> FESTIVAL;
            case 39 -> RESTAURANT;
            case 32 -> LODGING;
            default -> null;
        };
    }
}
