package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.VisitCheckStatus;

import java.time.LocalDate;
import java.util.List;

public record VisitCheckResponse(
        Long tripId,
        String title,
        VisitCheckStatus status,
        int checked,
        int total,
        List<Day> days
) {
    public record Day(int dayNumber, LocalDate date, List<Item> items) {
    }

    public record Item(Long itemId, Long contentId, String name, String thumbnailUrl, Boolean visited, Integer rating) {
    }
}
