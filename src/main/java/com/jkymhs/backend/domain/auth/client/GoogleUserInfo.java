package com.jkymhs.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

/**
 * 구글 userinfo 응답 중 로그인에 쓰는 값.
 * sub는 구글 계정마다 고정된 고유 ID다. 이메일은 바뀔 수 있어서 계정을 찾을 때는 sub를 먼저 본다.
 */
public record GoogleUserInfo(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified
) {

    // 이메일 가입 계정과 같은 기준으로 비교·저장하도록 앞뒤 공백을 없애고 소문자로 맞춘다
    public GoogleUserInfo {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
