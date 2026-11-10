package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.VisitCheckStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 방문 확인 화면 (확정 일정안의 장소들).
 *
 * @param checked 답한 장소 수
 * @param total   전체 장소 수
 */
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

    /**
     * @param visited 답하지 않았으면 null
     * @param rating  안 갔거나 답하지 않았으면 null
     */
    public record Item(Long itemId, Long contentId, String name, String thumbnailUrl, Boolean visited, Integer rating) {
    }
}
