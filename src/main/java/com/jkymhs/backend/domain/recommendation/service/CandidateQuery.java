package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.user.entity.TravelStyle;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * @param districtIds 비어 있으면 시·도 전체
 * @param styles      실제 적용할 여행 스타일. 비어 있으면 "모든 스타일" (온보딩을 건너뛴 회원)
 * @param pet         반려동물과 간다 (반려동물 불가로 확인된 곳은 뺀다)
 * @param penalized   점수를 깎을 곳: 다시 추천에서 빼달라고 한 곳 + 직전 추천 코스에 있던 곳
 */
public record CandidateQuery(
        Long userId,
        Long sidoId,
        List<Long> districtIds,
        LocalDate startDate,
        LocalDate endDate,
        Companion companion,
        boolean indoor,
        Set<TravelStyle> styles,
        boolean pet,
        List<Long> requiredIds,
        Set<Long> penalized
) {
}
