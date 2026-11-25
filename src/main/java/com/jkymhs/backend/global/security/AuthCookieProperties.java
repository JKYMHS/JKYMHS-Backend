package com.jkymhs.backend.global.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(
        boolean secure,
        @NotNull @Pattern(regexp = "Strict|Lax|None", message = "Strict, Lax, None 중 하나여야 합니다") String sameSite
) {

    public AuthCookieProperties {
        // SameSite=None은 Secure 없으면 브라우저가 쿠키를 조용히 버림
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException(
                    "auth.cookie.same-site=None이면 auth.cookie.secure는 true여야 합니다. 브라우저가 Secure 없는 SameSite=None 쿠키를 버립니다.");
        }
    }
}
