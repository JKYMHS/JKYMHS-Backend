package com.jkymhs.backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @DefaultValue List<String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins.forEach(CorsProperties::validate);
    }

    private static void validate(String origin) {
        if (origin.contains("*")) {
            throw invalid(origin, "*를 쓸 수 없습니다. 쿠키를 함께 보내는 CORS라서 정확한 주소만 허용합니다");
        }
        URI uri = parse(origin);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw invalid(origin, "http:// 또는 https://로 시작해야 합니다");
        }
        if (uri.getHost() == null) {
            throw invalid(origin, "호스트가 없습니다");
        }
        if (uri.getUserInfo() != null) {
            throw invalid(origin, "사용자 정보(user@)를 넣을 수 없습니다");
        }
        if (!uri.getRawPath().isEmpty()) {
            throw invalid(origin, "경로나 끝의 /를 넣을 수 없습니다");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalid(origin, "?나 #을 넣을 수 없습니다");
        }
    }

    private static URI parse(String origin) {
        try {
            return new URI(origin);
        } catch (URISyntaxException e) {
            throw invalid(origin, "주소 형식이 올바르지 않습니다");
        }
    }

    private static IllegalArgumentException invalid(String origin, String reason) {
        return new IllegalArgumentException("cors.allowed-origins 값이 올바르지 않습니다 [" + origin + "]: " + reason);
    }
}
