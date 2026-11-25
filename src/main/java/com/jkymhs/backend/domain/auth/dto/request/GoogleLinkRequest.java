package com.jkymhs.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLinkRequest(
        @NotBlank(message = "linkToken을 입력해주세요.") String linkToken,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
