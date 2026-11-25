package com.jkymhs.backend.domain.content.dto.response;

import com.jkymhs.backend.domain.content.entity.ContentType;

import java.util.List;

public record NearbyPicksResponse(List<Item> items) {

    public record Item(Long id, ContentType kind, String name, String category, String thumbnailUrl,
                       double lat, double lng, int distanceMeters, int walkMinutes) {
    }
}
