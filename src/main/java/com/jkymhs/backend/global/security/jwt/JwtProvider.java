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

@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration accessTokenExpiration;

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProvider(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        // @Size를 쓰면 시작 실패 로그에 secret이 찍힘
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTokenExpiration = properties.accessTokenExpiration();
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration.toMillis()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .sig().clear().add(Jwts.SIG.HS256).and()
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
