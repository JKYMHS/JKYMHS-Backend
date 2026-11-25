package com.jkymhs.backend.domain.calendar.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jkymhs.backend.domain.calendar.entity.CalendarFailureReason;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;

import java.util.List;

public record CalendarJobStatusResponse(
        Long jobId,
        CalendarJobType type,
        CalendarJobStatus status,
        int total,
        int succeeded,
        int failed,
        List<FailedItem> failedItems,
        List<Trip> trips,
        boolean retryable,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer pollAfterMs
) {
    public record FailedItem(Long itemId, Long tripId, String name, String startLabel,
                             CalendarFailureReason reasonCode, String reason) {
    }

    public record Trip(Long tripId, String title, int succeeded, int total) {
    }
}
