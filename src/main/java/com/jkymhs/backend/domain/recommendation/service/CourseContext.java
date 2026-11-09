package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.route.service.LatLng;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 코스 검증·시각 계산에 필요한 요청 조건.
 *
 * @param candidates     후보 id → 후보
 * @param firstDayStart  첫째 날 시작 시각 (요청 값)
 * @param laterDayStart  둘째 날부터 시작 시각 (취향의 시간대)
 * @param kids           아이와 감 (20:00 넘어 끝나는 장소 금지)
 */
public record CourseContext(
        Map<Long, Candidate> candidates,
        List<Long> requiredIds,
        LocalDate startDate,
        int days,
        int minPerDay,
        int maxPerDay,
        boolean kids,
        LocalTime firstDayStart,
        LocalTime laterDayStart,
        String originName,
        LatLng origin,
        int courses
) {

    public LocalTime dayStart(int dayNumber) {
        return dayNumber == 1 ? firstDayStart : laterDayStart;
    }
}
