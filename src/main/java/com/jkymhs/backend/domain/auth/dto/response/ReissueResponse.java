package com.jkymhs.backend.domain.auth.dto.response;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;

/**
 * refreshToken도 새 값이다. 이전 리프레시 토큰은 폐기됐으므로 FE는 반드시 새 값으로 바꿔 저장해야 한다.
 */
public record ReissueResponse(
        String accessToken,
        String refreshToken
) {

    public static ReissueResponse from(IssuedTokens tokens) {
        return new ReissueResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
