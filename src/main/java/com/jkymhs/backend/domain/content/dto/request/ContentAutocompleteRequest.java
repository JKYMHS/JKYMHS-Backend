package com.jkymhs.backend.domain.content.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;

/**
 * GET /api/contents/autocomplete 쿼리 파라미터. q의 2자 규칙은 특수문자를 지운 뒤에 봐야 해서 서비스(SearchKeyword)가 검사한다.
 *
 * @param regionId 고른 지역 (그 지역 결과를 앞에 둔다)
 * @param tab      없으면 장소·축제 모두
 */
public record ContentAutocompleteRequest(String q, Long regionId, CategoryTab tab) {
}
