package com.jkymhs.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleAccountLinkRequest(
        @NotBlank(message = "구글 인가 코드를 입력해주세요.")
        String code,

        @NotBlank(message = "redirectUri를 입력해주세요.")
        String redirectUri
) {
}
