package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.global.web.CodeEnum;

import java.util.EnumSet;
import java.util.Set;

/**
 * 탐색 홈·검색 시작 화면의 빠른 칩. 하나만 고를 수 있다. FE 값은 소문자다.
 *
 * 분류 칩(palace_village 등)은 categories 표의 CHIP 묶음 코드와 같은 값이라
 * {@code CategoryService.lclsCodesOf(code())}로 분류 코드를 얻는다 (어떤 분류가 들어가는지는 시드 표가 정한다).
 * all은 FE의 "전체" 칩이라 거르지 않는다.
 */
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

    /** 분류 코드(lcls1/2/3)로 거르는 칩인지 */
    public boolean isClassification() {
        return classification;
    }

    public boolean allowedIn(CategoryTab tab) {
        return tabs.contains(tab);
    }
}
