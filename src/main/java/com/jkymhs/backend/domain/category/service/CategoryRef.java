package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.Category;
import com.jkymhs.backend.domain.category.entity.CategoryGroupType;

public record CategoryRef(Long id, String code, String name, CategoryGroupType groupType) {

    static CategoryRef from(Category category) {
        return new CategoryRef(category.getId(), category.getCode(), category.getName(), category.getGroupType());
    }
}
