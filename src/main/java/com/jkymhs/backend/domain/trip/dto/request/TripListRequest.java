package com.jkymhs.backend.domain.trip.dto.request;

import com.jkymhs.backend.domain.trip.service.TripFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 여행 목록 쿼리 파라미터 ({@code ?filter=upcoming&cursor=…&size=20}).
 */
public record TripListRequest(
        TripFilter filter,
        String cursor,
        @Min(value = 1, message = "size는 1~50 사이로 입력해주세요.")
        @Max(value = 50, message = "size는 1~50 사이로 입력해주세요.") Integer size
) {

    public TripFilter filterOrDefault() {
        return filter == null ? TripFilter.ALL : filter;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
