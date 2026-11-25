package com.jkymhs.backend.domain.auth.dto.response;

public record EmailVerifyResponse(String verificationToken, long expiresIn) {
}
