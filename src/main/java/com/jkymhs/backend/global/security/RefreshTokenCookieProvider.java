package com.jkymhs.backend.global.security;

import com.jkymhs.backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";

    private static final String COOKIE_PATH = "/api/auth";

    private final AuthCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public ResponseCookie create(String refreshToken) {
        return builder(refreshToken)
                .maxAge(jwtProperties.refreshTokenExpiration())
                .build();
    }

    public ResponseCookie expire() {
        return builder("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .path(COOKIE_PATH)
                .secure(cookieProperties.secure())
                .httpOnly(true)
                .sameSite(cookieProperties.sameSite());
    }
}
