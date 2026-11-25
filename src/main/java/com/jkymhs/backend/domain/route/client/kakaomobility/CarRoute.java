package com.jkymhs.backend.domain.route.client.kakaomobility;

import com.jkymhs.backend.domain.route.service.LatLng;

import java.util.List;

public record CarRoute(int durationSeconds, int distanceMeters, List<LatLng> path) {
    public int minutes() {
        return Math.max(1, (int) Math.ceil(durationSeconds / 60.0));
    }
}
