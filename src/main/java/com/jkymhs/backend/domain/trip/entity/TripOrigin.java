package com.jkymhs.backend.domain.trip.entity;

/**
 * 여행 출발지 (trips.origin_* 컬럼 묶음).
 */
public record TripOrigin(String name, String address, double lat, double lng) {
}
