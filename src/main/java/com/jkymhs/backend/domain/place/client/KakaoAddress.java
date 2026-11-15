package com.jkymhs.backend.domain.place.client;

/**
 * 좌표 → 주소 변환 결과.
 *
 * @param buildingName 도로명 주소의 건물 이름. 없으면 null
 * @param roadAddress  도로명 주소. 없으면 null
 */
public record KakaoAddress(String address, String roadAddress, String buildingName) {
}
