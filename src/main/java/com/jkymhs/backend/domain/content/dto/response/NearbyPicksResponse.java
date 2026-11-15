package com.jkymhs.backend.domain.content.dto.response;

import com.jkymhs.backend.domain.content.entity.ContentType;

import java.util.List;

/**
 * 상세 화면 "함께 가기 좋은 곳" (도보 15분 ≈ 1km 안의 관광지·축제, 최대 4곳).
 */
public record NearbyPicksResponse(List<Item> items) {

    /**
     * @param walkMinutes 분속 67m(시속 약 4km)로 걸었을 때 분, 올림
     */
    public record Item(Long id, ContentType kind, String name, String category, String thumbnailUrl,
                       double lat, double lng, int distanceMeters, int walkMinutes) {
    }
}
