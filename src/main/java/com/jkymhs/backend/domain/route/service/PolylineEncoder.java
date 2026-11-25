package com.jkymhs.backend.domain.route.service;

import java.util.List;

public final class PolylineEncoder {
    private PolylineEncoder() {
    }

    public static String encode(List<LatLng> points) {
        StringBuilder result = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (LatLng point : points) {
            long lat = Math.round(point.lat() * 1e5);
            long lng = Math.round(point.lng() * 1e5);
            encodeValue(lat - prevLat, result);
            encodeValue(lng - prevLng, result);
            prevLat = lat;
            prevLng = lng;
        }
        return result.toString();
    }

    public static String straight(LatLng from, LatLng to) {
        return encode(List.of(from, to));
    }

    private static void encodeValue(long value, StringBuilder result) {
        long v = value < 0 ? ~(value << 1) : value << 1;
        while (v >= 0x20) {
            result.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        result.append((char) (v + 63));
    }
}
