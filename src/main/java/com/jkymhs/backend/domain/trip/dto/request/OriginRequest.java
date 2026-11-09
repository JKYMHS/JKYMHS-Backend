package com.jkymhs.backend.domain.trip.dto.request;

import com.jkymhs.backend.domain.trip.entity.TripOrigin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 출발지 (출발지 검색 API 결과를 그대로 보낸다).
 */
public record OriginRequest(
        @NotBlank(message = "출발지 이름을 입력해주세요.")
        @Size(max = 100, message = "출발지 이름은 100자까지 입력할 수 있습니다.") String name,
        @Size(max = 255, message = "출발지 주소는 255자까지 입력할 수 있습니다.") String address,
        @NotNull(message = "출발지 위도를 입력해주세요.")
        @DecimalMin(value = "-90", message = "위도가 올바르지 않습니다.")
        @DecimalMax(value = "90", message = "위도가 올바르지 않습니다.") Double lat,
        @NotNull(message = "출발지 경도를 입력해주세요.")
        @DecimalMin(value = "-180", message = "경도가 올바르지 않습니다.")
        @DecimalMax(value = "180", message = "경도가 올바르지 않습니다.") Double lng
) {

    public TripOrigin toOrigin() {
        return new TripOrigin(name.trim(), address, lat, lng);
    }
}
