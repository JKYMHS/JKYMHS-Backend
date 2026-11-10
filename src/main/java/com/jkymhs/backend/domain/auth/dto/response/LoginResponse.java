package com.jkymhs.backend.domain.auth.dto.response;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;

/**
 * 로그인 성공 응답. 회원가입도 가입 후 바로 로그인 상태가 되므로 같은 응답을 쓴다.
 * FE는 onboardingStatus가 NOT_STARTED면 온보딩, 나머지는 탐색 홈으로 보낸다.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        OnboardingStatus onboardingStatus
) {

    public static LoginResponse of(IssuedTokens tokens, OnboardingStatus onboardingStatus) {
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), onboardingStatus);
    }
}
