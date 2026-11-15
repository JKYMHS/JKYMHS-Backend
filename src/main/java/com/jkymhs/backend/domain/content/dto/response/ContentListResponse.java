package com.jkymhs.backend.domain.content.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 탐색 목록 한 페이지.
 *
 * @param counts 첫 페이지에만 준다 (다음 페이지에서는 null). 탭 머리의 "축제 9 · 관광지 8" 숫자다
 * @param asOf   진행 상태·영업 상태를 계산한 기준 날짜 (KST). 관광지 탭의 "오늘 11.09 월 기준" 표시에 쓴다
 */
public record ContentListResponse(
        List<ContentItemResponse> items,
        String nextCursor,
        boolean hasNext,
        TabCounts counts,
        LocalDate asOf
) {

    /**
     * 같은 검색어·지역·지도 범위에서 탭별 개수. 각 탭의 기본 상태로 센다 (축제는 진행 중 + 예정, 카테고리·칩·상태 필터는 빼고).
     */
    public record TabCounts(int festival, int place) {
    }
}
