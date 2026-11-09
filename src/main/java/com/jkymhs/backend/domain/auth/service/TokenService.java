package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.security.jwt.JwtProperties;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 액세스 토큰 + 리프레시 토큰 발급.
 * 회원가입·로그인·구글 로그인이 모두 로그인 성공 후 이 메서드를 부른다.
 *
 * 리프레시 토큰은 JWT가 아니라 32바이트 랜덤 문자열이다.
 * 어차피 재발급할 때마다 DB에서 폐기 여부를 확인해야 해서 JWT 서명으로 얻는 이점이 없고,
 * 랜덤 문자열이면 서명 키가 유출돼도 리프레시 토큰을 위조할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public IssuedTokens issue(User user, String userAgent) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration());

        // DB에는 원문 대신 해시만 저장한다
        refreshTokenRepository.save(RefreshToken.issue(user, hash(refreshToken), userAgent, expiresAt));
        return new IssuedTokens(accessToken, refreshToken);
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 해시를 16진수 문자열(64자)로 만든다. 같은 입력이면 항상 같은 값이라 DB에서 찾을 수 있다
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
