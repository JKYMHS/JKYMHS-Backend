package com.jkymhs.backend.domain.calendar.dto.response;

public record CalendarUploadResponse(Long jobId, int itemCount, int pollAfterMs) {
}
