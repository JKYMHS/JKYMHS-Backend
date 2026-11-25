package com.jkymhs.backend.domain.calendar.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CalendarUploadRequest(
        String calendarId,
        @Size(min = 1, message = "올릴 일정을 하나 이상 골라주세요.") List<@NotNull Long> itemIds
) {
}
