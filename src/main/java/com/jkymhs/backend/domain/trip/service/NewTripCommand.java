package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.TripOrigin;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.util.List;

/**
 * 새 여행 만들기 입력 (여행 만들기 API와 AI 추천 "새 여행으로 저장"이 같이 쓴다).
 *
 * @param regionIds 시·도 또는 시·군·구 id 1~5개
 * @param origin    출발지. 없으면 null
 */
public record NewTripCommand(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        List<Long> regionIds,
        Companion companion,
        ChildAgeGroup childAgeGroup,
        TripOrigin origin
) {
}
