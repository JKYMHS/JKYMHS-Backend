package com.jkymhs.backend.domain.content.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 콘텐츠 종류. FE 값(kind)은 소문자다. TourAPI contentTypeId와의 관계는 {@link #ofTourTypeId}에 있다.
 * 관광지(12)와 문화시설(14)은 화면에서 구분하지 않아서 둘 다 ATTRACTION이다 (원래 값은 contents.tour_content_type_id에 남긴다).
 */
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

    /**
     * @return 다루지 않는 종류(25 여행코스, 28 레포츠, 38 쇼핑)이면 null
     */
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
