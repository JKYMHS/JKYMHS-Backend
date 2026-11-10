package com.jkymhs.backend.domain.trip.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 목록. counts와 visitCheckPending은 첫 페이지(cursor 없음)에서만 채우고 다음 페이지부터는 null이다
 * (탭 숫자·배너는 화면에 한 번만 그리므로 페이지마다 다시 셀 필요가 없다).
 */
public record TripListResponse(
        List<TripSummaryResponse> items,
        String nextCursor,
        boolean hasNext,
        Counts counts,
        VisitCheckPending visitCheckPending
) {

    public record Counts(long all, long upcoming, long past, long unconfirmed) {
    }

    public record VisitCheckPending(Long tripId, String title, LocalDate endDate) {
    }
}
