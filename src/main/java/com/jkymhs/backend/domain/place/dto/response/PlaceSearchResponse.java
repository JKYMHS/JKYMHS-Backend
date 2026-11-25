package com.jkymhs.backend.domain.place.dto.response;

import java.util.List;

public record PlaceSearchResponse(List<Item> items) {

    public record Item(String placeId, String name, String address, String roadAddress, String category,
                       double lat, double lng) {
    }
}
