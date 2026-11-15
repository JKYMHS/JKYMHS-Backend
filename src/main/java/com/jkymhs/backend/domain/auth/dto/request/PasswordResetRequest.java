package com.jkymhs.backend.domain.auth.dto.request;

import com.jkymhs.backend.domain.user.dto.request.PasswordRule;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 비밀번호 재설정. verificationToken은 이메일 인증(purpose RESET_PASSWORD)을 마치고 받은 토큰이다.
 */
public record PasswordResetRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "인증 토큰을 입력해주세요.")
        String verificationToken,

        // 빈 값이면 두 검증이 동시에 실패하는데, 메시지가 같아야 어느 쪽이 먼저 나와도 응답이 같다
        @NotBlank(message = PasswordRule.MESSAGE)
        @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
        String newPassword
) {

    /*
     * 인증 메일 발송·검증과 같은 기준(앞뒤 공백 제거, 소문자)으로 맞춘다.
     * 기준이 다르면 인증할 때 저장한 Redis 키(auth:verified:RESET_PASSWORD:{email})를 찾지 못한다.
     */
    public PasswordResetRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
