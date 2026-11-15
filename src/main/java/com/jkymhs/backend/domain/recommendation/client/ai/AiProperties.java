package com.jkymhs.backend.domain.recommendation.client.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code ai.*}. AI 서버 계약이 아직 확정되지 않아서(§5 Q1) 기본값은 서버 안에서 코스를 만드는 mock이다.
 *
 * @param provider mock(기본) 또는 http
 * @param baseUrl  http일 때 AI 서버 주소 (예: http://ai:8000). 끝에 / 없이
 * @param apiKey   http일 때 Authorization: Bearer 값. 비어 있으면 헤더를 보내지 않는다
 * @param timeout  AI 응답 대기 시간. 넘으면 기본 추천 코스로 대신한다
 */
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
        // http인데 주소가 없으면 추천이 매번 기본 코스로만 끝나므로, 설정 실수를 서버 시작 때 알린다
        if (HTTP.equals(provider) && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalArgumentException("ai.provider=http이면 ai.base-url(AI_BASE_URL)이 필요합니다.");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
