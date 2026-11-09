package com.jkymhs.backend.domain.wish.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * GET /api/wishes 쿼리 파라미터.
 *
 * @param tab             festival | place (place = 축제가 아닌 모든 종류: 관광지·음식점·숙박)
 * @param hideEnded       축제 탭: 끝난 축제 숨기기
 * @param hideClosedToday 장소 탭: 지금 영업하지 않거나 오늘이 쉬는 날인 곳 숨기기
 * @param cursor          이전 응답의 nextCursor
 * @param size            기본 20, 최대 50
 */
public record WishListRequest(
        @NotNull(message = "잘못된 필터 값입니다.") CategoryTab tab,
        Boolean hideEnded,
        Boolean hideClosedToday,
        String cursor,
        @Min(value = 1, message = "잘못된 필터 값입니다.") @Max(value = 50, message = "잘못된 필터 값입니다.") Integer size
) {
}
