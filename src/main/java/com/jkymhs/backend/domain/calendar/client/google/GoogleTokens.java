package com.jkymhs.backend.domain.calendar.client.google;

/**
 * 인가 코드 교환 결과.
 *
 * @param refreshToken 동의 화면을 access_type=offline, prompt=consent로 열어야 온다. 없으면 null
 * @param scope        사용자가 실제로 허용한 권한 (공백으로 구분)
 */
public record GoogleTokens(String accessToken, String refreshToken, String scope) {
}
