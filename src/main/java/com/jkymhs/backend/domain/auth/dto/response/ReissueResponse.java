package com.jkymhs.backend.domain.auth.dto.response;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;

public record ReissueResponse(
        String accessToken
) {

    public static ReissueResponse from(IssuedTokens tokens) {
        return new ReissueResponse(tokens.accessToken());
    }
}
