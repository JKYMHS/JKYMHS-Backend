package com.jkymhs.backend.domain.content.dto.response;

import com.jkymhs.backend.domain.content.entity.ContentType;

import java.util.List;

public record ContentNearbyResponse(List<Item> items, int totalCount, int radiusMeters) {

    public record Item(Long id, ContentType kind, String name, String address, String category, String thumbnailUrl,
                       double lat, double lng, int distanceMeters) {
    }
}
