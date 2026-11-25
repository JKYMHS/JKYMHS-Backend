package com.jkymhs.backend.domain.category.entity;

import com.jkymhs.backend.global.web.CodeEnum;

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

    public static CategoryTab of(CategoryGroupType groupType) {
        for (CategoryTab tab : values()) {
            if (tab.groupType == groupType) {
                return tab;
            }
        }
        return null;
    }
}
