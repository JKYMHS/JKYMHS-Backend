package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.global.web.CodeEnum;

import java.util.EnumSet;
import java.util.Set;

public enum ContentChip implements CodeEnum {
    ALL("all", false, EnumSet.allOf(CategoryTab.class)),
    ONGOING("ongoing", false, EnumSet.of(CategoryTab.FESTIVAL)),
    WEEKEND("weekend", false, EnumSet.of(CategoryTab.FESTIVAL)),
    POPULAR800("popular800", false, EnumSet.of(CategoryTab.FESTIVAL)),
    OPEN_NOW("open_now", false, EnumSet.of(CategoryTab.PLACE)),
    PALACE_VILLAGE("palace_village", true, EnumSet.of(CategoryTab.PLACE)),
    MUSEUM_EXHIBIT("museum_exhibit", true, EnumSet.of(CategoryTab.PLACE)),
    VIEW_PARK("view_park", true, EnumSet.of(CategoryTab.PLACE)),
    NIGHT("night", false, EnumSet.allOf(CategoryTab.class)),
    WITH_KIDS("with_kids", true, EnumSet.allOf(CategoryTab.class));

    private final String code;
    private final boolean classification;
    private final Set<CategoryTab> tabs;

    ContentChip(String code, boolean classification, Set<CategoryTab> tabs) {
        this.code = code;
        this.classification = classification;
        this.tabs = tabs;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isClassification() {
        return classification;
    }

    public boolean allowedIn(CategoryTab tab) {
        return tabs.contains(tab);
    }
}
