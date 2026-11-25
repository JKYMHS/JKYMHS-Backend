package com.jkymhs.backend.domain.route.service;

import java.util.Locale;

public final class RouteRedisKeys {
    private RouteRedisKeys() {
    }

    public static String route(LatLng from, LatLng to) {
        return String.format(Locale.ROOT, "route:%.4f,%.4f:%.4f,%.4f", from.lat(), from.lng(), to.lat(), to.lng());
    }
}
