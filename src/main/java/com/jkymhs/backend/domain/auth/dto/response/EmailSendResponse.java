package com.jkymhs.backend.domain.auth.dto.response;

public record EmailSendResponse(long expiresIn, long resendAfter) {
}
