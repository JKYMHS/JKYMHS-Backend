package com.jkymhs.backend.domain.category.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;

/**
 * @param tab festival | place. 없으면 두 탭 모두. 다른 값이면 400 INVALID_INPUT (CodeEnumConverterFactory가 변환에 실패한다)
 */
public record CategoryListRequest(CategoryTab tab) {
}
