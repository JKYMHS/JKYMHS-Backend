package com.jkymhs.backend.domain.auth.dto.request;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "인증 토큰을 입력해주세요.")
        String verificationToken,

        @NotBlank(message = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,20}$", message = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.")
        String password,

        @NotNull(message = "필수 약관에 모두 동의해야 합니다.")
        @Valid
        Consents consents
) {

    public SignupRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public record Consents(
            @NotNull(message = "필수 약관에 모두 동의해야 합니다.")
            @AssertTrue(message = "필수 약관에 모두 동의해야 합니다.")
            Boolean age14,

            @NotNull(message = "필수 약관에 모두 동의해야 합니다.")
            @AssertTrue(message = "필수 약관에 모두 동의해야 합니다.")
            Boolean terms,

            @NotNull(message = "필수 약관에 모두 동의해야 합니다.")
            @AssertTrue(message = "필수 약관에 모두 동의해야 합니다.")
            Boolean privacy,

            @NotNull(message = "선택 약관 동의 여부를 입력해주세요.")
            Boolean behaviorData,

            @NotNull(message = "선택 약관 동의 여부를 입력해주세요.")
            Boolean emailNotification
    ) {

        public Set<ConsentType> agreedTypes() {
            Set<ConsentType> agreed = EnumSet.noneOf(ConsentType.class);
            if (age14) {
                agreed.add(ConsentType.AGE_14);
            }
            if (terms) {
                agreed.add(ConsentType.TERMS);
            }
            if (privacy) {
                agreed.add(ConsentType.PRIVACY);
            }
            if (behaviorData) {
                agreed.add(ConsentType.BEHAVIOR_DATA);
            }
            if (emailNotification) {
                agreed.add(ConsentType.EMAIL_NOTIFICATION);
            }
            return agreed;
        }
    }
}
