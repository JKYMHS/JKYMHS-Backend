package com.jkymhs.backend.domain.content.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum IoType implements CodeEnum {
    INDOOR,
    OUTDOOR,
    MIXED;

    @Override
    public String code() {
        return name();
    }

    public static IoType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (IoType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
