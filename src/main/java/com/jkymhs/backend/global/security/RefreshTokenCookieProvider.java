package com.jkymhs.backend.global.security;

import com.jkymhs.backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키를 만드는 곳. 속성이 어긋나지 않도록 발급과 삭제 모두 여기서 만든다.
 * 브라우저는 이름·Path·Domain이 모두 같은 쿠키만 같은 쿠키로 본다. 하나라도 다르면 삭제 쿠키를 보내도 원래 쿠키가 남는다.
 *
 * - HttpOnly: 자바스크립트에서 읽을 수 없다. XSS가 있어도 리프레시 토큰은 빼낼 수 없다
 * - Secure: HTTPS 요청에만 붙는다
 * - SameSite: 다른 사이트에서 보낸 요청에 쿠키를 붙일지 정한다 (기본값 Lax는 다른 사이트의 POST에 붙이지 않는다)
 * - Path=/api/auth: 재발급·로그아웃 API에만 붙는다. 다른 API 요청에는 실리지 않는다
 * - Domain 없음: 이 쿠키를 내려준 호스트로만 돌아간다
 */
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

    // Max-Age=0이면 브라우저가 바로 지운다
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
