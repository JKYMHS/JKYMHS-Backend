package com.jkymhs.backend.domain.trip.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml {@code app.*}. 메일 속 링크처럼 서버가 FE 주소를 알아야 할 때 쓴다.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String frontendBaseUrl) {

    /** 끝의 / 를 떼고 경로를 붙인다 */
    public String frontendUrl(String path) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return base + path;
    }
}
