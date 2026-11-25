package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.route.service.LatLng;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
