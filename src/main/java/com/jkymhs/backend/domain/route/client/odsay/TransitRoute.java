package com.jkymhs.backend.domain.route.client.odsay;

/**
 * ODsay 대중교통 길찾기의 첫 번째(추천) 경로.
 *
 * @param summary 탄 노선 순서와 환승 수 ("지하철 1호선 → 버스 472 · 환승 1회")
 */
public record TransitRoute(int minutes, int distanceMeters, String summary) {
}
