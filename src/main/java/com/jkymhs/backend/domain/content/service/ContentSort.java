package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.global.web.CodeEnum;

public enum ContentSort implements CodeEnum {
    LATEST("latest"),
    POPULAR("popular"),
    NAME("name");

    private final String code;

    ContentSort(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static ContentSort defaultOf(CategoryTab tab) {
        return tab == CategoryTab.FESTIVAL ? LATEST : POPULAR;
    }

    public boolean allowedIn(CategoryTab tab) {
        return tab == CategoryTab.FESTIVAL ? this != NAME : this != LATEST;
    }
}
