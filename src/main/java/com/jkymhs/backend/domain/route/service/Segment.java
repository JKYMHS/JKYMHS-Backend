package com.jkymhs.backend.domain.route.service;

/**
 * 경로를 계산할 구간 하나 (출발 → 도착).
 */
public record Segment(LatLng from, LatLng to) {
}
