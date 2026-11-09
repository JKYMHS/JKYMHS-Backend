package com.jkymhs.backend.domain.recommendation.dto.response;

import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 추천 요청·다시 추천의 202 응답. FE는 pollAfterMs 뒤에 결과 API를 부른다.
 *
 * @param left     이 요청까지 쓰고 남은 오늘 횟수
 * @param warnings 꼭 포함할 장소 중 기간·지역이 맞지 않는 곳 (막지 않고 알려만 준다)
 */
public record RecommendationAcceptedResponse(
        Long requestId,
        Long previousRequestId,
        RecommendationStatus status,
        int left,
        long pollAfterMs,
        List<Warning> warnings
) {

    /**
     * @param code          OUT_OF_PERIOD | OUT_OF_REGION
     * @param availableFrom 축제 시작일 (OUT_OF_PERIOD일 때)
     */
    public record Warning(Long contentId, String code, LocalDate availableFrom, LocalDate availableTo) {
    }
}
