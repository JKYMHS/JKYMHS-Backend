package com.jkymhs.backend.domain.auth.dto.response;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;

public record GoogleLinkResponse(
        String accessToken,
        String refreshToken,
        OnboardingStatus onboardingStatus
) {

    public static GoogleLinkResponse of(IssuedTokens tokens, OnboardingStatus onboardingStatus) {
        return new GoogleLinkResponse(tokens.accessToken(), tokens.refreshToken(), onboardingStatus);
    }
}
