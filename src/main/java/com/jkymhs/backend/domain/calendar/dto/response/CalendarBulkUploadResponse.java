package com.jkymhs.backend.domain.calendar.dto.response;

import com.jkymhs.backend.domain.calendar.service.BulkSkipReason;

import java.util.List;

public record CalendarBulkUploadResponse(Long jobId, List<Trip> trips) {
    public record Trip(Long tripId, int itemCount, BulkSkipReason skipped) {
    }
}
