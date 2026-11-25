package com.jkymhs.backend.domain.calendar.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CalendarConnectRequest(
        @NotBlank(message = "인가 코드를 입력해주세요.") String code,
        @NotBlank(message = "redirectUri를 입력해주세요.") String redirectUri
) {
}
