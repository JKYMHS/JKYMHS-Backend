package com.jkymhs.backend.domain.content.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;

public record ContentAutocompleteRequest(String q, Long regionId, CategoryTab tab) {
}
