package com.jkymhs.backend.domain.place.client;

/**
 * 카카오 로컬 키워드·주소 검색 결과 한 건.
 *
 * @param placeId     카카오 장소 id. 주소 검색 결과는 null
 * @param roadAddress 도로명 주소. 없으면 null
 * @param category    카카오 category_group_name ("지하철역", "숙박"). 없으면 null
 */
public record KakaoPlace(String placeId, String name, String address, String roadAddress, String category,
                         double lat, double lng) {
}
