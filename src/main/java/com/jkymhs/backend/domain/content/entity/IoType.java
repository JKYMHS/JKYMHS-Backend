package com.jkymhs.backend.domain.content.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 실내·실외 구분. 관광공사 소분류(lcls3)마다 tools/tourapi/indoor_outdoor.csv에 손으로 정해둔 값이다
 * (TourAPI는 이 값을 주지 않는다). 비 오는 날 AI 추천이 실내 위주로 고를 때 쓴다.
 */
public enum IoType implements CodeEnum {
    INDOOR,
    OUTDOOR,
    MIXED;

    @Override
    public String code() {
        return name();
    }

    /** CSV 값("INDOOR", 빈 값 등)을 읽는다. 모르는 값이나 빈 값은 null(정해지지 않음). */
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
