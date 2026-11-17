package com.jkymhs.backend.domain.wish.dto.response;

import java.util.List;

/**
 * 찜 목록 한 페이지.
 *
 * @param counts 첫 페이지에만 준다. 숨기기 옵션과 상관없이 탭별 전체 찜 수다 (탭 머리의 숫자)
 */
public record WishListResponse(
        List<WishItemResponse> items,
        String nextCursor,
        boolean hasNext,
        TabCounts counts
) {

    public record TabCounts(int festival, int place) {
    }
}
