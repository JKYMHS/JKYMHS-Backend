package com.jkymhs.backend.domain.trip.dto.response;

import java.time.LocalDate;
import java.util.List;

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
