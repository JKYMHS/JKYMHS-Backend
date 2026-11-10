package com.jkymhs.backend.domain.behavior.repository;

/**
 * 콘텐츠별 개수 (인기 점수 배치 입력). JPQL {@code select new …ContentCount(…)}로 바로 만든다.
 */
public record ContentCount(Long contentId, long count) {
}
