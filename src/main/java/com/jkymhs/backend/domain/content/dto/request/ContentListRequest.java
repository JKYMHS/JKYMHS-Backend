package com.jkymhs.backend.domain.content.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.content.service.ContentChip;
import com.jkymhs.backend.domain.content.service.ContentSort;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * GET /api/contents 쿼리 파라미터. 탭에 맞지 않는 값(관광지 탭의 status, 축제 탭의 sort=name 등)은 서비스가 400으로 막는다.
 *
 * @param tab        festival | place (place = 관광지·문화시설)
 * @param q          검색어 (이름·주소). 특수문자를 지우고 2자 이상
 * @param regionId   시·도 또는 시·군·구 id
 * @param categoryId 그 탭의 필터 카테고리 id
 * @param status     축제 전용, 쉼표로 여러 개 (ONGOING,UPCOMING). 없으면 진행 중 + 예정
 * @param sort       축제 latest(기본)|popular, 관광지 popular(기본)|name
 * @param chip       빠른 칩 하나
 * @param bbox       지도 범위 "남서위도,남서경도,북동위도,북동경도"
 * @param cursor     이전 응답의 nextCursor. 첫 페이지면 없음
 * @param size       기본 20, 최대 explore.page-size-max(50)
 */
public record ContentListRequest(
        @NotNull(message = "잘못된 필터 값입니다.") CategoryTab tab,
        String q,
        Long regionId,
        Long categoryId,
        List<FestivalStatus> status,
        ContentSort sort,
        ContentChip chip,
        String bbox,
        String cursor,
        @Min(value = 1, message = "잘못된 필터 값입니다.") Integer size
) {
}
