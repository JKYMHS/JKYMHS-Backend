package com.jkymhs.backend.domain.place.dto.response;

import java.util.List;

/**
 * 출발지 검색 결과 (최대 10개: 장소 이름 검색 결과 먼저, 그다음 주소 검색 결과).
 */
public record PlaceSearchResponse(List<Item> items) {

    /**
     * @param placeId     카카오 장소 id (문자열). 주소 검색 결과는 null
     * @param roadAddress 도로명 주소. 없으면 null
     * @param category    카카오 분류 이름 ("지하철역"). 없으면 null
     */
    public record Item(String placeId, String name, String address, String roadAddress, String category,
                       double lat, double lng) {
    }
}
