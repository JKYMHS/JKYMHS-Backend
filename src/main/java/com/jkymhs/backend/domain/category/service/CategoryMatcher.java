package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.CategoryGroupType;

import java.util.List;
import java.util.Set;

/**
 * 콘텐츠 분류 코드(lcls1/2/3) → 필터 카테고리 id. 적재할 때 contents.category_id를 정하는 데 쓴다.
 * 축제는 축제 필터(FESTIVAL_FILTER)에서, 그 밖은 장소 필터(PLACE_FILTER)에서 고른다. 칩(CHIP)은 저장하지 않는다.
 * 여러 카테고리에 걸리면 정렬 순서(sort_order)가 앞선 것을 고른다.
 */
public final class CategoryMatcher {

    private final List<Entry> entries;

    CategoryMatcher(List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * @return 맞는 카테고리가 없으면 null (음식점·숙박 등)
     */
    public Long categoryIdFor(boolean festival, String lcls1, String lcls2, String lcls3) {
        CategoryGroupType group = festival ? CategoryGroupType.FESTIVAL_FILTER : CategoryGroupType.PLACE_FILTER;
        for (Entry entry : entries) {
            if (entry.groupType() == group && (matches(entry, lcls1) || matches(entry, lcls2) || matches(entry, lcls3))) {
                return entry.categoryId();
            }
        }
        return null;
    }

    private static boolean matches(Entry entry, String code) {
        return code != null && entry.lclsCodes().contains(code);
    }

    /** sort_order 순으로 넣는다. */
    record Entry(Long categoryId, CategoryGroupType groupType, Set<String> lclsCodes) {
    }
}
