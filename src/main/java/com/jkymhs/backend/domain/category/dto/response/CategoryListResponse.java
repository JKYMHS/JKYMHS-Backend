package com.jkymhs.backend.domain.category.dto.response;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.category.service.CategoryRef;

import java.util.List;

public record CategoryListResponse(List<Item> categories) {

    public static CategoryListResponse from(List<CategoryRef> categories) {
        return new CategoryListResponse(categories.stream()
                .map(c -> new Item(c.id(), c.code(), c.name(), CategoryTab.of(c.groupType())))
                .toList());
    }

    public record Item(Long id, String code, String name, CategoryTab tab) {
    }
}
