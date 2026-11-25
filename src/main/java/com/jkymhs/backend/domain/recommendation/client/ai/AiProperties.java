package com.jkymhs.backend.domain.recommendation.client.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        @DefaultValue("mock") @Pattern(regexp = "mock|http", message = "ai.provider는 mock 또는 http여야 합니다.")
        String provider,
        String baseUrl,
        String apiKey,
        @DefaultValue("20s") @NotNull Duration timeout
) {
    public static final String MOCK = "mock";
    public static final String HTTP = "http";

    public AiProperties {
        if (HTTP.equals(provider) && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalArgumentException("ai.provider=http이면 ai.base-url(AI_BASE_URL)이 필요합니다.");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
