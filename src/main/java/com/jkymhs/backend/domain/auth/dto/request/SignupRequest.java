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

        // 영문과 숫자를 하나 이상씩 포함한 8~20자, 공백 불가 (그 밖의 특수문자는 허용)
        // 빈 값이면 두 검증이 동시에 실패하는데, 메시지가 같아야 어느 쪽이 먼저 나와도 응답이 같다
        @NotBlank(message = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,20}$", message = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.")
        String password,

        // @Valid가 있어야 안쪽 Consents의 검증까지 실행된다
        @NotNull(message = "필수 약관에 모두 동의해야 합니다.")
        @Valid
        Consents consents
) {

    /*
     * 이메일은 앞뒤 공백을 없애고 소문자로 바꿔서 다룬다.
     * "User@Example.com"과 "user@example.com"이 다른 회원으로 가입되거나, 대소문자만 바꿔서 로그인 실패 횟수 제한을 피하는 일을 막는다.
     * Locale.ROOT: 터키어 등 일부 언어 설정에서는 "I".toLowerCase()가 "i"가 아닌 다른 문자가 되기 때문이다.
     */
    public SignupRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 5개 항목 모두 true/false를 보내야 한다 (빠지면 400). 선택 항목에 값이 없을 때 "미동의"로 짐작하지 않는다.
     */
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
