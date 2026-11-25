package com.jkymhs.backend.domain.trip.service;

public record TripSyncView(Long tripId, String title, TripSyncState sync) {
}
