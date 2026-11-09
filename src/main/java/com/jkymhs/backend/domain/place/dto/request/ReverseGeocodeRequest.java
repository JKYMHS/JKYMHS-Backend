package com.jkymhs.backend.domain.place.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * GET /api/places/reverse-geocode?lat=&lng= (현재 위치 → 주소). 좌표는 저장하지 않고 로그에도 남기지 않는다.
 */
public record ReverseGeocodeRequest(
        @NotNull(message = "좌표 값이 올바르지 않습니다.")
        @DecimalMin(value = "-90", message = "좌표 값이 올바르지 않습니다.")
        @DecimalMax(value = "90", message = "좌표 값이 올바르지 않습니다.") Double lat,
        @NotNull(message = "좌표 값이 올바르지 않습니다.")
        @DecimalMin(value = "-180", message = "좌표 값이 올바르지 않습니다.")
        @DecimalMax(value = "180", message = "좌표 값이 올바르지 않습니다.") Double lng
) {
}
