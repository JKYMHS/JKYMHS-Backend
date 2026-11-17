package com.jkymhs.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword,

        @NotBlank(message = PasswordRule.MESSAGE)
        @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
        String newPassword
) {
}
