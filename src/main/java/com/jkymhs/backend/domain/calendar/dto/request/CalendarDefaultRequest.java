package com.jkymhs.backend.domain.calendar.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CalendarDefaultRequest(@NotBlank(message = "캘린더를 선택해주세요.") String calendarId) {
}
