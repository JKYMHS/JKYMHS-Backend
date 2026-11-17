package com.jkymhs.backend.domain.user.dto.response;

/**
 * 비밀번호를 바꾸면 모든 기기의 로그인이 끊기므로, 지금 기기가 계속 쓸 새 액세스 토큰을 준다.
 * 새 리프레시 토큰은 본문이 아니라 쿠키(Set-Cookie)로 내려간다.
 */
public record PasswordChangeResponse(String accessToken) {
}
