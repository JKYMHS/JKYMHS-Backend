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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 액세스 토큰 + 리프레시 토큰 발급, 재발급(rotation), 로그아웃.
 * 회원가입·로그인·구글 로그인이 모두 로그인 성공 후 issue를 부른다.
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

    /**
     * 리프레시 토큰으로 새 액세스 토큰 + 새 리프레시 토큰을 발급하고, 받은 토큰은 바로 폐기한다 (rotation).
     * 새 리프레시 토큰의 유효기간은 지금부터 다시 jwt.refresh-token-expiration(14일)이다.
     *
     * 이미 폐기된 토큰이 다시 오면 탈취로 본다. 토큰을 훔친 사람과 진짜 사용자 중 누가 먼저 썼는지 알 수 없으니
     * 그 회원의 모든 기기를 로그아웃시켜서 둘 다 다시 로그인하게 한다.
     *
     * noRollbackFor: 기본 설정에서는 메서드가 런타임 예외(CustomException)를 던지면 트랜잭션이 롤백된다.
     * 그러면 401을 던지기 직전에 한 "모든 토큰 폐기"까지 취소돼서 탈취 대응이 사라진다.
     * 그래서 CustomException이 나도 그때까지의 변경은 커밋한다.
     * (다른 @Transactional 메서드 안에서 부르면 바깥 트랜잭션 규칙으로 롤백되므로 컨트롤러에서 바로 부른다)
     */
    @Transactional(noRollbackFor = CustomException.class)
    public IssuedTokens reissue(String refreshToken, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(refreshToken))
                .orElseThrow(TokenService::invalidRefreshToken);

        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(token.getUser().getId(), now);
            throw invalidRefreshToken();
        }
        if (token.isExpired(now)) {
            throw invalidRefreshToken();
        }

        token.revoke(now);
        return issue(token.getUser(), userAgent);
    }

    /**
     * 이 기기의 리프레시 토큰만 폐기한다. 다른 기기의 로그인은 유지된다.
     * 토큰이 없거나, 다른 회원의 것이거나, 이미 폐기·만료됐으면 아무것도 하지 않는다.
     * 어느 경우든 같은 204를 줘서, 남의 토큰이 살아 있는지 떠보는 데 쓸 수 없게 한다.
     */
    @Transactional
    public void logout(Long userId, String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.getUser().getId().equals(userId))
                .filter(token -> !token.isRevoked() && !token.isExpired(now))
                .ifPresent(token -> token.revoke(now));
    }

    // 없음·만료·폐기 모두 같은 응답이다. FE는 이유와 상관없이 로그인 화면으로 보내면 된다
    private static CustomException invalidRefreshToken() {
        return new CustomException(ErrorCode.INVALID_REFRESH_TOKEN, "만료·폐기·위조된 리프레시 토큰");
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
