package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;

import java.util.Locale;

/**
 * 인증 기능이 쓰는 Redis 키 이름 (노션 "Redis 키 설계" 문서 기준).
 * 키 문자열을 여러 클래스에 흩어 쓰면 오타 하나로 서로 다른 키를 보게 되므로 여기서만 만든다.
 *
 * 이메일은 요청 DTO에서 이미 소문자로 맞추지만, 혹시 정규화되지 않은 값이 들어와도 "User@x.com"과 "user@x.com"이
 * 다른 키가 되지 않도록 여기서 한 번 더 맞춘다 (로그인 실패 횟수를 이메일 철자 바꾸기로 피하지 못하게).
 */
public final class AuthRedisKeys {

    private AuthRedisKeys() {
    }

    // Hash {code, failCount, sentAt}, TTL 5분
    public static String emailCode(VerificationPurpose purpose, String email) {
        return "auth:code:" + purpose + ":" + normalize(email);
    }

    // String (verificationToken), TTL 10분
    public static String emailVerified(VerificationPurpose purpose, String email) {
        return "auth:verified:" + purpose + ":" + normalize(email);
    }

    // String (JSON {memberId, googleSub, googleEmail}), TTL 10분
    public static String googleLink(String linkToken) {
        return "auth:link:" + linkToken;
    }

    // Hash {failCount, lockedUntil}, TTL 마지막 실패로부터 10분
    public static String loginFail(String email) {
        return "login:fail:" + normalize(email);
    }

    // String (요청 횟수), TTL 1분
    public static String rateLimit(RateLimitType type, String ip) {
        return "rate:" + type + ":" + ip;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
