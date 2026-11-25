package com.jkymhs.backend.domain.place.client;

public record KakaoPlace(String placeId, String name, String address, String roadAddress, String category,
                         double lat, double lng) {
}
