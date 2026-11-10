package com.jkymhs.backend.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;

/**
 * 구글 로그인 응답. result에 따라 채워지는 필드가 다르다.
 * - LOGGED_IN / SIGNED_UP: accessToken, refreshToken, onboardingStatus
 * - LINK_REQUIRED: linkToken, email
 *
 * {@code NON_NULL}이라 값이 null인 필드는 JSON에서 아예 빠진다 (LINK_REQUIRED 응답에 "accessToken": null이 나가지 않는다).
 * 생성자 대신 결과별 정적 메서드로 만들어서 result와 필드 조합이 어긋날 수 없게 했다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleLoginResponse(
        GoogleLoginResult result,
        String accessToken,
        String refreshToken,
        OnboardingStatus onboardingStatus,
        String linkToken,
        String email
) {

    public static GoogleLoginResponse loggedIn(IssuedTokens tokens, OnboardingStatus onboardingStatus) {
        return withTokens(GoogleLoginResult.LOGGED_IN, tokens, onboardingStatus);
    }

    public static GoogleLoginResponse signedUp(IssuedTokens tokens, OnboardingStatus onboardingStatus) {
        return withTokens(GoogleLoginResult.SIGNED_UP, tokens, onboardingStatus);
    }

    public static GoogleLoginResponse linkRequired(String linkToken, String email) {
        return new GoogleLoginResponse(GoogleLoginResult.LINK_REQUIRED, null, null, null, linkToken, email);
    }

    private static GoogleLoginResponse withTokens(GoogleLoginResult result, IssuedTokens tokens,
                                                  OnboardingStatus onboardingStatus) {
        return new GoogleLoginResponse(result, tokens.accessToken(), tokens.refreshToken(), onboardingStatus, null, null);
    }
}
