package com.jkymhs.backend.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalTime;

/**
 * @param resetAt  다음 초기화 시각 (내일 00:00 KST, "YYYY-MM-DDT00:00:00+09:00")
 * @param defaults 추천 요청 화면의 기본값 (마지막으로 고른 동행, 첫날 시작 시각)
 */
public record RecommendationQuotaResponse(int total, int used, int left, String resetAt, Defaults defaults) {

    /**
     * @param companion 마지막 추천 요청의 동행. 한 번도 안 했으면 null
     * @param startTime 취향의 "여행 시작 시간대" 시각
     */
    public record Defaults(Companion companion, @JsonFormat(pattern = "HH:mm") LocalTime startTime) {
    }
}
