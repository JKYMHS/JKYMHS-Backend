package com.jkymhs.backend.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.recommendation.entity.FailureReason;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStep;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 결과 조회 응답. 상태마다 모양이 달라서 세 record로 나눈다 (PENDING 진행, DONE/FALLBACK 결과, FAILED).
 * Jackson은 실제 객체의 record 필드를 그대로 쓴다.
 */
public sealed interface RecommendationStatusResponse {

    record Pending(
            Long requestId,
            RecommendationStatus status,
            RecommendationStep step,
            int stepIndex,
            int stepTotal,
            long pollAfterMs,
            Progress progress
    ) implements RecommendationStatusResponse {
    }

    /**
     * @param candidateCount 점수를 매긴 후보 수
     * @param selectedCount  코스에 들어간 장소 수 (중복 제외)
     */
    record Progress(int candidateCount, int selectedCount) {
    }

    /**
     * @param isFallback     AI 대신 서버가 만든 기본 추천 코스
     * @param indoorShortage 실내를 원했는데 실내 장소가 절반이 안 된다
     */
    record Result(
            Long requestId,
            RecommendationStatus status,
            boolean isFallback,
            boolean indoorShortage,
            Long previousRequestId,
            LocalDateTime expiresAt,
            Conditions conditions,
            List<CourseResponse> courses
    ) implements RecommendationStatusResponse {
    }

    record Conditions(
            Long regionId,
            String regionName,
            List<Long> districtIds,
            List<String> districtNames,
            LocalDate startDate,
            LocalDate endDate,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            Companion companion,
            ChildAgeGroup childAgeGroup,
            boolean indoor,
            Origin origin
    ) {
    }

    record Origin(String name, String address, Double lat, Double lng) {
    }

    /**
     * @param refunded 실패한 요청은 횟수에서 빠진다 (항상 true)
     */
    record Failed(Long requestId, RecommendationStatus status, boolean refunded, FailureReason reason)
            implements RecommendationStatusResponse {
    }
}
