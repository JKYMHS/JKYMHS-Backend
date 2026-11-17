package com.jkymhs.backend.domain.category.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 탐색 탭. FE 값은 소문자("festival", "place")라 {@link CodeEnum}으로 바꾼다.
 */
public enum CategoryTab implements CodeEnum {
    FESTIVAL("festival", CategoryGroupType.FESTIVAL_FILTER),
    PLACE("place", CategoryGroupType.PLACE_FILTER);

    private final String code;
    private final CategoryGroupType groupType;

    CategoryTab(String code, CategoryGroupType groupType) {
        this.code = code;
        this.groupType = groupType;
    }

    @Override
    public String code() {
        return code;
    }

    public CategoryGroupType groupType() {
        return groupType;
    }

    /** 필터 카테고리가 속한 탭. 칩은 탭이 없어서 null이다. */
    public static CategoryTab of(CategoryGroupType groupType) {
        for (CategoryTab tab : values()) {
            if (tab.groupType == groupType) {
                return tab;
            }
        }
        return null;
    }
}
