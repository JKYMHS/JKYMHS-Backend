package com.jkymhs.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(max = 72, message = "비밀번호가 너무 깁니다.")
        String password
) {

    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
