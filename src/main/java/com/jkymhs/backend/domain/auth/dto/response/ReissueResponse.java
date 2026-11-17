package com.jkymhs.backend.domain.auth.dto.response;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;

/**
 * 새 리프레시 토큰은 쿠키로 내려간다. 브라우저가 같은 이름의 쿠키를 덮어쓰므로 FE가 따로 저장할 것은 없다.
 */
public record ReissueResponse(
        String accessToken
) {

    public static ReissueResponse from(IssuedTokens tokens) {
        return new ReissueResponse(tokens.accessToken());
    }
}
