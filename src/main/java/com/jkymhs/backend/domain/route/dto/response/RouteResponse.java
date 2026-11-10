package com.jkymhs.backend.domain.route.dto.response;

import com.jkymhs.backend.domain.route.service.MoveMode;
import com.jkymhs.backend.domain.route.service.RouteResult;

/**
 * 구간 하나의 이동. 일정 상세의 move와 같은 모양이다 (+ distanceMeters).
 */
public record RouteResponse(
        MoveMode mode,
        int minutes,
        MoveMode altMode,
        Integer altMinutes,
        String summary,
        String polyline,
        boolean isEstimated,
        int distanceMeters
) {

    public static RouteResponse from(RouteResult result) {
        return new RouteResponse(result.mode(), result.minutes(), result.altMode(), result.altMinutes(),
                result.summary(), result.polyline(), result.estimated(), result.distanceMeters());
    }
}
