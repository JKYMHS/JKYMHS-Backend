package com.jkymhs.backend.domain.content.dto.request;

import com.jkymhs.backend.domain.content.service.NearbyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * GET /api/contents/nearby 쿼리 파라미터.
 *
 * @param radius 미터. 없으면 종류별 기본값 (음식점 1000, 숙박 3000)
 * @param size   기본 5
 */
public record ContentNearbyRequest(
        @NotNull(message = "잘못된 필터 값입니다.") NearbyType type,
        @NotNull(message = "좌표 값이 올바르지 않습니다.")
        @DecimalMin(value = "-90", message = "좌표 값이 올바르지 않습니다.")
        @DecimalMax(value = "90", message = "좌표 값이 올바르지 않습니다.") Double lat,
        @NotNull(message = "좌표 값이 올바르지 않습니다.")
        @DecimalMin(value = "-180", message = "좌표 값이 올바르지 않습니다.")
        @DecimalMax(value = "180", message = "좌표 값이 올바르지 않습니다.") Double lng,
        @Min(value = 1, message = "반경은 1~5000m로 입력해주세요.")
        @Max(value = 5000, message = "반경은 1~5000m로 입력해주세요.") Integer radius,
        @Min(value = 1, message = "잘못된 필터 값입니다.")
        @Max(value = 20, message = "잘못된 필터 값입니다.") Integer size
) {
}
