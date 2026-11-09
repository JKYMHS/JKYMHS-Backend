package com.jkymhs.backend.domain.trip.service;

/**
 * 캘린더 연동 화면의 "동기화된 여행" 한 줄.
 */
public record TripSyncView(Long tripId, String title, TripSyncState sync) {
}
