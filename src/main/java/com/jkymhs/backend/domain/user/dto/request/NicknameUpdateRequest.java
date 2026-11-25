package com.jkymhs.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        @NotBlank(message = "이름은 1~30자로 입력해주세요.")
        @Size(max = 30, message = "이름은 1~30자로 입력해주세요.")
        String nickname
) {

    public NicknameUpdateRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
