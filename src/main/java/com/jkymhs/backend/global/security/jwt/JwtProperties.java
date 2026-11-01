package com.jkymhs.backend.global.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml의 jwt.* 설정을 묶은 객체.
 * {@code @Validated}라서 값이 비었거나 짧으면 서버가 시작할 때 바로 실패한다 (요청이 들어온 뒤에야 터지는 것보다 낫다).
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret은 32자 이상이어야 합니다.") String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {
}
