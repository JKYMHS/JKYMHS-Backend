package com.jkymhs.backend.domain.auth.service;

/**
 * 서비스가 컨트롤러로 넘기는 로그인 결과. JSON으로 나갈 본문과 쿠키로 나갈 리프레시 토큰을 나눠 담는다.
 * 응답 DTO에 리프레시 토큰 필드를 두고 직렬화만 막으면(@JsonIgnore) 애너테이션 하나 빠졌을 때 토큰이 JSON으로 새어 나간다.
 * 아예 필드가 없으면 그럴 일이 없다.
 *
 * @param refreshToken 쿠키로 내려줄 토큰. 아직 로그인이 아닌 결과(구글 LINK_REQUIRED)는 null이다
 */
public record AuthResult<T>(T body, String refreshToken) {
}
