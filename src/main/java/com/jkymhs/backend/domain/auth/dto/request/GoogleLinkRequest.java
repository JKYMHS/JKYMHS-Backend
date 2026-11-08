package com.jkymhs.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// 기존 비밀번호를 확인만 하므로 로그인처럼 형식 규칙(8~20자) 없이 비어 있는지만 본다
public record GoogleLinkRequest(
        @NotBlank(message = "linkToken을 입력해주세요.") String linkToken,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
