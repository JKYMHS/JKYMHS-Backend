package com.jkymhs.backend.domain.auth.dto.response;

/**
 * @param expiresIn   인증 코드 유효 시간(초)
 * @param resendAfter 재전송 가능까지 남은 시간(초)
 */
public record EmailSendResponse(long expiresIn, long resendAfter) {
}
