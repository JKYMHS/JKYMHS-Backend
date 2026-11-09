package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 주변 검색(CONTENT_NEARBY) 종류. FE 값은 대문자 그대로다 (일정 화면의 "이 근처 식사"·"오늘 밤 숙소").
 * 기본 반경은 기능 명세서 003-04 기준이다: 식사는 걸어갈 만한 1km, 숙소는 차로 움직이는 3km.
 */
public enum NearbyType implements CodeEnum {
    RESTAURANT(ContentType.RESTAURANT, 1000),
    LODGING(ContentType.LODGING, 3000);

    private final ContentType contentType;
    private final int defaultRadiusMeters;

    NearbyType(ContentType contentType, int defaultRadiusMeters) {
        this.contentType = contentType;
        this.defaultRadiusMeters = defaultRadiusMeters;
    }

    @Override
    public String code() {
        return name();
    }

    public ContentType contentType() {
        return contentType;
    }

    public int defaultRadiusMeters() {
        return defaultRadiusMeters;
    }
}
