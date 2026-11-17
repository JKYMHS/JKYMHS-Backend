package com.jkymhs.backend.domain.route.dto.response;

import java.util.List;

/**
 * 요청한 구간 순서 그대로의 결과.
 */
public record RouteCalculateResponse(List<RouteResponse> routes) {
}
