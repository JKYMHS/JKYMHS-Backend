package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.global.web.CodeEnum;

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
