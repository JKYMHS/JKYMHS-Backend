package com.jkymhs.backend.domain.place.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "kakao-local")
public record KakaoLocalProperties(
        @DefaultValue("https://dapi.kakao.com") @NotBlank String baseUrl,
        String restApiKey,
        @DefaultValue("3s") @NotNull Duration timeout
) {

    public boolean hasRestApiKey() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
