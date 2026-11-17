package com.jkymhs.backend.domain.content.client;

import java.util.List;
import java.util.Map;

/**
 * TourAPI 목록 응답 한 페이지. 항목은 필드 이름 → 문자열 값으로 둔다 (TourAPI는 숫자도 문자열로 주고, 오퍼레이션마다 필드가 다르다).
 */
public record TourApiPage(List<Map<String, String>> items, int pageNo, int numOfRows, int totalCount) {

    public boolean hasNext() {
        return !items.isEmpty() && (long) pageNo * numOfRows < totalCount;
    }
}
