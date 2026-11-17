package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.Category;
import com.jkymhs.backend.domain.category.entity.CategoryGroupType;

/**
 * 다른 도메인에 넘기는 카테고리 정보.
 */
public record CategoryRef(Long id, String code, String name, CategoryGroupType groupType) {

    static CategoryRef from(Category category) {
        return new CategoryRef(category.getId(), category.getCode(), category.getName(), category.getGroupType());
    }
}
