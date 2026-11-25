package com.jkymhs.backend.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

public record GoogleUserInfo(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified
) {

    public GoogleUserInfo {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
