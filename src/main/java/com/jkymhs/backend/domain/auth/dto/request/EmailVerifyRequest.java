package com.jkymhs.backend.domain.auth.dto.request;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record EmailVerifyRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        @NotNull(message = "인증 용도를 입력해주세요.")
        VerificationPurpose purpose,

        @NotBlank(message = "인증 코드를 입력해주세요.")
        @Pattern(regexp = "\\d{6}", message = "인증 코드는 숫자 6자리입니다.")
        String code
) {

    // 발송 요청과 같은 규칙으로 정규화해야 같은 Redis 키를 찾는다
    public EmailVerifyRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
