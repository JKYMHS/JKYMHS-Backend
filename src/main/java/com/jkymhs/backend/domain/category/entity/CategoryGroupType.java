package com.jkymhs.backend.domain.category.entity;

/**
 * 카테고리 묶음.
 * PLACE_FILTER·FESTIVAL_FILTER는 탐색 화면 카테고리 필터(장소 탭·축제 탭)이고, 콘텐츠마다 하나가 정해진다(contents.category_id).
 * CHIP은 홈 화면 빠른 칩("아이와 함께" 등)이라 여러 카테고리 분류에 걸쳐 있고, 콘텐츠에 저장하지 않고 조회할 때 분류 코드로 거른다.
 */
public enum CategoryGroupType {
    PLACE_FILTER,
    FESTIVAL_FILTER,
    CHIP
}
