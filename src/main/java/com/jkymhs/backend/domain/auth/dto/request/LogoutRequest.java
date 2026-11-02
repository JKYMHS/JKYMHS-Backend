package com.jkymhs.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 액세스 토큰만으로는 어느 기기인지 알 수 없어서, 이 기기의 리프레시 토큰을 같이 받아 그것만 폐기한다.
 */
public record LogoutRequest(
        @NotBlank(message = "리프레시 토큰을 입력해주세요.")
        String refreshToken
) {
}
