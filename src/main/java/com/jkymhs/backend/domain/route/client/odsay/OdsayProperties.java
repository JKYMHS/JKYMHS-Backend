package com.jkymhs.backend.domain.route.client.odsay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "odsay")
public record OdsayProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @NotNull Duration timeout
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
