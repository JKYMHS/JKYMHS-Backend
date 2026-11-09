package com.jkymhs.backend.domain.route.service;

import java.util.List;

/**
 * 구글 encoded polyline 알고리즘 (정밀도 5). 좌표 수백 개를 짧은 문자열 하나로 줄여서 응답·DB 크기를 줄인다.
 * FE 지도 라이브러리가 그대로 풀 수 있는 표준 형식이다.
 */
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
