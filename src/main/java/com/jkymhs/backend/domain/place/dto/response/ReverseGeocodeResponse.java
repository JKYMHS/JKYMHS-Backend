package com.jkymhs.backend.domain.place.dto.response;

/**
 * @param name        화면에 보여줄 이름 (건물 이름이 있으면 건물 이름, 없으면 주소)
 * @param address     지번 주소
 * @param roadAddress 도로명 주소. 없으면 null
 */
public record ReverseGeocodeResponse(String name, String address, String roadAddress) {
}
