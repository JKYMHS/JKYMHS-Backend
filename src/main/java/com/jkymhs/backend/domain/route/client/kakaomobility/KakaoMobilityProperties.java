package com.jkymhs.backend.domain.route.client.kakaomobility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code kakao-mobility.*}.
 *
 * @param restApiKey 카카오 로컬과 같은 REST API 키. 비어 있으면 호출하지 않고 "결과 없음"으로 본다 (서버는 뜬다)
 */
@Validated
@ConfigurationProperties(prefix = "kakao-mobility")
public record KakaoMobilityProperties(
        @NotBlank String baseUrl,
        String restApiKey,
        @NotNull Duration timeout
) {

    public boolean hasApiKey() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
