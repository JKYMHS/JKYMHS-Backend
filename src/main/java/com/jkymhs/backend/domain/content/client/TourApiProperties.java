package com.jkymhs.backend.domain.content.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "tour-api")
public record TourApiProperties(
        @NotBlank String baseUrl,
        String serviceKey,
        @Min(1) int dailyCallLimit,
        @NotNull Duration timeout,
        @DefaultValue("1s") @NotNull Duration retryBackoff,
        @Valid @DefaultValue Sync sync
) {

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public record Sync(
            @DefaultValue("0 0 4 * * *") String cron,
            @DefaultValue("300") @Min(0) int introPerRun
    ) {
    }
}
