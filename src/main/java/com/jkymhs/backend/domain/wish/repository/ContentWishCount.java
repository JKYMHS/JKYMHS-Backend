package com.jkymhs.backend.domain.wish.repository;

/**
 * 콘텐츠별 새 찜 수 (인기 점수 배치 입력).
 */
public record ContentWishCount(Long contentId, long count) {
}
