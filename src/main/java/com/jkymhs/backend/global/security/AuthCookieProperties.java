package com.jkymhs.backend.global.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 auth.cookie.* 설정. 배포 환경(도메인 구성)에 따라 달라지는 쿠키 속성만 뺐다.
 * {@code @Validated}와 생성자 검사 덕분에 잘못된 값이면 서버가 시작할 때 바로 실패한다.
 */
@Validated
@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(
        boolean secure,
        @NotNull @Pattern(regexp = "Strict|Lax|None", message = "Strict, Lax, None 중 하나여야 합니다") String sameSite
) {

    public AuthCookieProperties {
        // 브라우저는 SameSite=None에 Secure가 없는 쿠키를 오류 없이 버린다. 재발급만 조용히 실패해서 원인을 찾기 어려우니 시작할 때 막는다
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException(
                    "auth.cookie.same-site=None이면 auth.cookie.secure는 true여야 합니다. 브라우저가 Secure 없는 SameSite=None 쿠키를 버립니다.");
        }
    }
}
