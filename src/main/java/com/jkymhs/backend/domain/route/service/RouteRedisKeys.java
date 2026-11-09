package com.jkymhs.backend.domain.route.service;

import java.util.Locale;

/**
 * 경로 계산 Redis 키.
 */
public final class RouteRedisKeys {

    private RouteRedisKeys() {
    }

    /**
     * 구간 경로 캐시. Hash(mode, minutes, altMode, altMinutes, summary, polyline, distance), TTL 7일.
     * 좌표는 소수 4자리(약 10m)로 반올림해서, 거의 같은 위치는 같은 키를 쓰게 한다.
     * 예: {@code route:37.5796,126.9770:37.5512,126.9882}
     */
    public static String route(LatLng from, LatLng to) {
        return String.format(Locale.ROOT, "route:%.4f,%.4f:%.4f,%.4f", from.lat(), from.lng(), to.lat(), to.lng());
    }
}
