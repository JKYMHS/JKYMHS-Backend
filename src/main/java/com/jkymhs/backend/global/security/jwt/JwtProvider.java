package com.jkymhs.backend.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * 액세스 토큰(JWT) 발급·검증.
 * 토큰에는 회원 ID(sub)만 넣는다. 이메일 같은 개인정보는 넣지 않는다 (JWT 본문은 암호화가 아니라 Base64라서 누구나 읽을 수 있다).
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration accessTokenExpiration;

    public JwtProvider(JwtProperties properties) {
        // HMAC-SHA 키. 32바이트 이상이면 HS256으로 서명된다
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = properties.accessTokenExpiration();
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration.toMillis()))
                .signWith(key)
                .compact();
    }

    /**
     * 서명과 만료 시각을 검증하고 회원 ID를 꺼낸다.
     *
     * @throws ExpiredJwtException 서명은 맞지만 만료된 토큰
     * @throws JwtException        서명이 틀렸거나 형식이 깨진 토큰
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("subject is not a user id");
        }
    }
}
