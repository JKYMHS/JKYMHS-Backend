package com.jkymhs.backend.domain.auth.dto.response;

/**
 * @param verificationToken 회원가입·비밀번호 재설정 요청에 그대로 넣는 일회용 토큰
 * @param expiresIn         토큰 유효 시간(초)
 */
public record EmailVerifyResponse(String verificationToken, long expiresIn) {
}
