package com.jkymhs.backend.domain.auth.dto.response;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;

public record LoginResponse(
        String accessToken,
        OnboardingStatus onboardingStatus
) {

    public static LoginResponse of(IssuedTokens tokens, OnboardingStatus onboardingStatus) {
        return new LoginResponse(tokens.accessToken(), onboardingStatus);
    }
}
