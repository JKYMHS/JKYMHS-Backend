package com.jkymhs.backend.domain.calendar.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param calendarId 올릴 캘린더. 없으면 이 여행에 마지막으로 쓴 캘린더, 그것도 없으면 기본 캘린더
 * @param itemIds    올릴 확정안 항목. 없으면(null) "아직 동기화되지 않은 항목 전부"
 */
public record CalendarUploadRequest(
        String calendarId,
        @Size(min = 1, message = "올릴 일정을 하나 이상 골라주세요.") List<@NotNull Long> itemIds
) {
}
