package com.jkymhs.backend.domain.user.dto.request;

public record WithdrawRequest(
        String password,
        String googleCode,
        String redirectUri
) {

    public static final WithdrawRequest EMPTY = new WithdrawRequest(null, null, null);
}
