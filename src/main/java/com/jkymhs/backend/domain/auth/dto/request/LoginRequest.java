package com.jkymhs.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

/**
 * 로그인은 형식 검증을 하지 않는다. 틀린 형식이어도 "이메일 또는 비밀번호가 올바르지 않습니다."로 같게 응답하면 된다.
 */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {

    // 가입할 때와 같은 규칙으로 정규화해야 같은 계정을 찾는다 (SignupRequest 참고)
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
