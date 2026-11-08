package com.jkymhs.backend.support;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 테스트에서 리프레시 토큰 쿠키를 확인하고 꺼내 쓰는 도구.
 * 기본 설정(application.yml)의 쿠키 속성이 그대로 나가는지 전체 문자열로 비교한다.
 * Expires는 ResponseCookie가 Max-Age와 같이 넣어주는 값이고, Domain이 없다는 것까지 이 비교로 확인된다.
 */
public final class RefreshTokenCookies {

    private static final String NAME = "refreshToken";

    private static final Pattern ISSUED = Pattern.compile(
            "refreshToken=([A-Za-z0-9_-]{43}); Path=/api/auth; Max-Age=1209600; Expires=[^;]+; Secure; HttpOnly; SameSite=Lax");

    private static final Pattern CLEARED = Pattern.compile(
            "refreshToken=; Path=/api/auth; Max-Age=0; Expires=[^;]+; Secure; HttpOnly; SameSite=Lax");

    private RefreshTokenCookies() {
    }

    public static Cookie cookie(String refreshToken) {
        return new Cookie(NAME, refreshToken);
    }

    public static ResultMatcher issued() {
        return result -> assertThat(setCookie(result)).matches(ISSUED);
    }

    public static ResultMatcher cleared() {
        return result -> assertThat(setCookie(result)).matches(CLEARED);
    }

    public static ResultMatcher absent() {
        return result -> assertThat(setCookie(result)).isNull();
    }

    public static String tokenOf(MvcResult result) {
        Matcher matcher = ISSUED.matcher(String.valueOf(setCookie(result)));
        assertThat(matcher.matches()).as("리프레시 토큰 쿠키가 있어야 한다: %s", setCookie(result)).isTrue();
        return matcher.group(1);
    }

    private static String setCookie(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }
}
