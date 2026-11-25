package com.jkymhs.backend.domain.route.service;

public record RouteResult(
        MoveMode mode,
        int minutes,
        MoveMode altMode,
        Integer altMinutes,
        String summary,
        String polyline,
        boolean estimated,
        int distanceMeters
) {
}
