package com.jkymhs.backend.domain.route.client.kakaomobility;

import com.jkymhs.backend.domain.route.service.LatLng;

import java.util.List;

/**
 * 카카오모빌리티 자동차 길찾기 결과.
 *
 * @param path 실제 도로를 따라가는 점들 (vertexes). 지도에 택시 경로를 그릴 때 쓴다
 */
public record CarRoute(int durationSeconds, int distanceMeters, List<LatLng> path) {

    public int minutes() {
        return Math.max(1, (int) Math.ceil(durationSeconds / 60.0));
    }
}
