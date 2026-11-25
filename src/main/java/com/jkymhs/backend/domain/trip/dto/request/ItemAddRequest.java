package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemAddRequest(
        @NotNull(message = "장소를 입력해주세요.") Long contentId,
        @NotNull(message = "날짜를 선택해주세요.") @Min(value = 1, message = "여행 기간 안의 날짜를 선택해주세요.") Integer dayNumber,
        Boolean allowDuplicate
) {
}
