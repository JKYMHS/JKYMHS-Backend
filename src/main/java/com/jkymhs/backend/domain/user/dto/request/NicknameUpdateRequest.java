package com.jkymhs.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        // 앞뒤 공백을 뺀 뒤 1~30자. 빈 값과 너무 긴 값이 같은 문구라서 어느 검증이 먼저 걸려도 응답이 같다
        @NotBlank(message = "이름은 1~30자로 입력해주세요.")
        @Size(max = 30, message = "이름은 1~30자로 입력해주세요.")
        String nickname
) {

    public NicknameUpdateRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
