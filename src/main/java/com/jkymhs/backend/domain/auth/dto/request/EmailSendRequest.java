package com.jkymhs.backend.domain.auth.dto.request;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record EmailSendRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        @NotNull(message = "인증 용도를 입력해주세요.")
        VerificationPurpose purpose
) {

    public EmailSendRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
