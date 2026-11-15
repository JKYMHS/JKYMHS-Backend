package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 탐색 목록 정렬. FE 값은 소문자다.
 * <ul>
 *   <li>latest(축제 기본): 시작일 빠른 순 → 이름 → id. "최신순"이지만 기능 명세서 002-03 기준으로 곧 시작하는 축제가 먼저다</li>
 *   <li>popular(관광지 기본, 축제 선택): 인기 점수 높은 순 → 이름 → id</li>
 *   <li>name(관광지 선택): 이름 → id</li>
 * </ul>
 * 마지막에 항상 id를 붙이는 이유: 이름·점수가 같은 항목이 있어도 순서가 하나로 정해져야 커서(마지막 항목 다음부터)가 빠짐·중복 없이 이어진다.
 */
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
