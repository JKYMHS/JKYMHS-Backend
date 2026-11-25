package com.jkymhs.backend.domain.calendar.dto.request;

import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CalendarBulkUploadRequest(
        @NotNull(message = "작업 종류를 입력해주세요.") CalendarJobType type,
        @NotEmpty(message = "올릴 여행을 하나 이상 골라주세요.") List<@NotNull Long> tripIds,
        String calendarId
) {
}
