package com.jkymhs.backend.domain.calendar.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 동의 화면에서 돌아온 인가 코드와, 동의 화면을 열 때 쓴 redirect_uri (구글이 둘이 같은지 확인한다).
 */
public record CalendarConnectRequest(
        @NotBlank(message = "인가 코드를 입력해주세요.") String code,
        @NotBlank(message = "redirectUri를 입력해주세요.") String redirectUri
) {
}
