package com.jkymhs.backend.domain.calendar.client.google;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

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
