package com.jkymhs.backend.domain.route.client.odsay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code odsay.*}.
 *
 * @param apiKey ODsay LAB 서버용 키. 비어 있으면 호출하지 않고 "결과 없음"으로 본다 (서버는 뜬다)
 */
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
