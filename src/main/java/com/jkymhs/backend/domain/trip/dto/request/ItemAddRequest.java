package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 상세 화면에서 "여행에 담기".
 *
 * @param allowDuplicate 같은 날에 이미 있는 장소라도 한 번 더 넣을지 (409를 받은 뒤 사용자가 "그래도 추가"를 누르면 true)
 */
public record ItemAddRequest(
        @NotNull(message = "장소를 입력해주세요.") Long contentId,
        @NotNull(message = "날짜를 선택해주세요.") @Min(value = 1, message = "여행 기간 안의 날짜를 선택해주세요.") Integer dayNumber,
        Boolean allowDuplicate
) {
}
