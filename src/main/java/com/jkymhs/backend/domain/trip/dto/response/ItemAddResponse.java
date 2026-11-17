package com.jkymhs.backend.domain.trip.dto.response;

/**
 * 담기 결과 (토스트 "○○ 여행 1일차에 담았어요"에 쓴다).
 *
 * @param festivalOutOfRange 축제를 축제 기간 밖의 날에 담았다 (경고 표시)
 */
public record ItemAddResponse(
        Long itemId,
        String tripTitle,
        String planName,
        int dayNumber,
        int sortOrder,
        boolean festivalOutOfRange
) {
}
