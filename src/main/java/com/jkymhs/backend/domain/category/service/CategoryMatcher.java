package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.CategoryGroupType;

import java.util.List;
import java.util.Set;

public final class CategoryMatcher {

    private final List<Entry> entries;

    CategoryMatcher(List<Entry> entries) {
        this.entries = entries;
    }

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

    record Entry(Long categoryId, CategoryGroupType groupType, Set<String> lclsCodes) {
    }
}
