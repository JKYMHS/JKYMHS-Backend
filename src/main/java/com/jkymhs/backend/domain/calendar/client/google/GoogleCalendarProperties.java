package com.jkymhs.backend.domain.calendar.client.google;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code google-calendar.*}.
 *
 * clientId·clientSecret은 구글 로그인(#6)과 같은 OAuth 클라이언트 값이다 (같은 환경변수 GOOGLE_CLIENT_*).
 * tokenEncryptionKey는 리프레시 토큰 암호화 키(base64로 32바이트). 비어 있어도 서버는 뜨고, 연동할 때만 503이 난다
 * (검사는 {@code CalendarTokenCipher}가 쓸 때 한다 — 여기서 @Size 등으로 검사하면 실패 로그에 키 값이 찍힌다).
 *
 * @param batchSize   작업자가 작업 하나에서 한 차례에 처리하는 항목 수
 * @param maxAttempts 429·5xx일 때 항목 하나를 다시 시도하는 최대 횟수
 * @param lease       작업자가 작업을 집은 뒤 이 시간 안에 끝내지 못하면(작업자가 죽음) 다른 작업자가 다시 집는다
 */
@Validated
@ConfigurationProperties(prefix = "google-calendar")
public record GoogleCalendarProperties(
        String clientId,
        String clientSecret,
        @NotBlank String tokenUri,
        @NotBlank String revokeUri,
        @NotBlank String userInfoUri,
        @NotBlank String apiBaseUrl,
        String tokenEncryptionKey,
        @DefaultValue("2s") @NotNull Duration pollInterval,
        @DefaultValue("50") @Min(1) int batchSize,
        @DefaultValue("3") @Min(1) int maxAttempts,
        @DefaultValue("5s") @NotNull Duration timeout,
        @DefaultValue("5s") @NotNull Duration backoffBase,
        @DefaultValue("5m") @NotNull Duration lease,
        @Valid @DefaultValue Executor executor
) {

    public record Executor(
            @DefaultValue("2") @Min(1) int coreSize,
            @DefaultValue("4") @Min(1) int maxSize,
            @DefaultValue("100") @Min(0) int queueCapacity
    ) {
    }
}
