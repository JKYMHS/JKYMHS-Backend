package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.security.jwt.JwtProperties;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public IssuedTokens issue(User user, String userAgent) {
        return issue(user, userAgent, UUID.randomUUID().toString());
    }

    private IssuedTokens issue(User user, String userAgent, String familyId) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration());

        refreshTokenRepository.save(RefreshToken.issue(user, familyId, hash(refreshToken), userAgent, expiresAt));
        return new IssuedTokens(accessToken, refreshToken);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public IssuedTokens reissue(String refreshToken, String userAgent) {
        if (!StringUtils.hasText(refreshToken)) {
            throw invalidRefreshToken();
        }
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(refreshToken))
                .orElseThrow(TokenService::invalidRefreshToken);

        if (token.isExpired(now)) {
            throw invalidRefreshToken();
        }
        if (token.isRevoked()) {
            if (token.isRotatedWithin(REUSE_GRACE, now)) {
                return issue(token.getUser(), userAgent, token.getFamilyId());
            }
            if (token.isRotated()) {
                refreshTokenRepository.revokeAllByUserId(token.getUser().getId(), now);
            }
            throw invalidRefreshToken();
        }

        token.rotate(now);
        return issue(token.getUser(), userAgent, token.getFamilyId());
    }

    @Transactional
    public void logout(Long userId, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByTokenHashForUpdate(hash(refreshToken))
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(userId, token.getFamilyId(), now));
    }

    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private static CustomException invalidRefreshToken() {
        return new CustomException(ErrorCode.INVALID_REFRESH_TOKEN, "만료·폐기·위조된 리프레시 토큰 또는 쿠키 없음");
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record IssuedTokens(String accessToken, String refreshToken) {
    }
}
