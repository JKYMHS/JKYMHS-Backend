package com.jkymhs.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 로그인은 형식 검증을 하지 않는다. 틀린 형식이어도 "이메일 또는 비밀번호가 올바르지 않습니다."로 같게 응답하면 된다.
 * 길이 상한만 둔다. 상한이 없으면 아주 긴 값이 Redis 키나 BCrypt 비교까지 그대로 간다.
 */
public record LoginRequest(
        // 이메일은 로그인 실패 기록의 Redis 키(auth:login:fail:{email})에 그대로 들어가고, 없는 이메일이어도 10분간 남는다.
        // 상한이 없으면 아주 긴 이메일로 요청할 때마다 그 크기의 키가 쌓인다. 가입할 때와 같은 254자로 막는다
        @NotBlank(message = "이메일을 입력해주세요.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        // BCrypt는 72바이트까지만 비밀번호로 쓰고, 그보다 긴 비밀번호는 가입할 때 저장되지 않는다.
        // 그래서 72자보다 긴 입력은 어떤 계정의 비밀번호와도 맞을 수 없다. 지금 가입 규칙(20자)이 아니라 이 한계로 막는다
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(max = 72, message = "비밀번호가 너무 깁니다.")
        String password
) {

    // 가입할 때와 같은 규칙으로 정규화해야 같은 계정을 찾는다 (SignupRequest 참고)
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
