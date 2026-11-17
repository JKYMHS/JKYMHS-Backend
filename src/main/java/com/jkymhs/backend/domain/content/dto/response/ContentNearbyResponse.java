package com.jkymhs.backend.domain.content.dto.response;

import com.jkymhs.backend.domain.content.entity.ContentType;

import java.util.List;

/**
 * 기준점 주변 음식점·숙박.
 *
 * @param totalCount   반경 안 전체 개수 ("주변 음식점 8곳 보기" 문구용. items는 size개까지만 준다)
 * @param radiusMeters 실제로 찾은 반경. 처음 반경에 하나도 없으면 두 배로 한 번 더 찾으므로 요청 값과 다를 수 있다
 */
public record ContentNearbyResponse(List<Item> items, int totalCount, int radiusMeters) {

    public record Item(Long id, ContentType kind, String name, String address, String category, String thumbnailUrl,
                       double lat, double lng, int distanceMeters) {
    }
}
