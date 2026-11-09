package com.jkymhs.backend.domain.place.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code kakao-local.*}.
 *
 * @param restApiKey 카카오 개발자 앱의 REST API 키. 비밀값이라 환경변수(KAKAO_REST_API_KEY)로만 넣는다.
 *                   비어 있어도 서버는 뜨고, 출발지 검색만 502로 실패한다
 */
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
