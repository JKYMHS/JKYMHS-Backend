package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;

import java.util.Locale;

public final class AuthRedisKeys {

    private AuthRedisKeys() {
    }

    public static String emailCode(VerificationPurpose purpose, String email) {
        return "auth:code:" + purpose + ":" + normalize(email);
    }

    public static String emailVerified(VerificationPurpose purpose, String email) {
        return "auth:verified:" + purpose + ":" + normalize(email);
    }

    public static String googleLink(String linkToken) {
        return "auth:link:" + linkToken;
    }

    public static String loginFail(String email) {
        return "login:fail:" + normalize(email);
    }

    public static String rateLimit(RateLimitType type, String ip) {
        return "rate:" + type + ":" + ip;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
