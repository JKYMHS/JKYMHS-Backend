package com.jkymhs.backend.domain.auth.dto.request;

import com.jkymhs.backend.domain.user.dto.request.PasswordRule;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record PasswordResetRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "인증 토큰을 입력해주세요.")
        String verificationToken,

        @NotBlank(message = PasswordRule.MESSAGE)
        @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
        String newPassword
) {

    public PasswordResetRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
