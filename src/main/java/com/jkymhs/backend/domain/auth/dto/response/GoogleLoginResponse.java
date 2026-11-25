package com.jkymhs.backend.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleLoginResponse(
        GoogleLoginResult result,
        String accessToken,
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
        return new GoogleLoginResponse(GoogleLoginResult.LINK_REQUIRED, null, null, linkToken, email);
    }

    private static GoogleLoginResponse withTokens(GoogleLoginResult result, IssuedTokens tokens,
                                                  OnboardingStatus onboardingStatus) {
        return new GoogleLoginResponse(result, tokens.accessToken(), onboardingStatus, null, null);
    }
}
