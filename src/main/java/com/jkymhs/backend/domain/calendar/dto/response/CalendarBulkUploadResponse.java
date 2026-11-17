package com.jkymhs.backend.domain.calendar.dto.response;

import com.jkymhs.backend.domain.calendar.service.BulkSkipReason;

import java.util.List;

public record CalendarBulkUploadResponse(Long jobId, List<Trip> trips) {

    /**
     * @param skipped 건너뛴 이유 (NOT_CONFIRMED 확정안 없음 / ALREADY_SYNCED 올릴 것이 없음). 올리면 null
     */
    public record Trip(Long tripId, int itemCount, BulkSkipReason skipped) {
    }
}
