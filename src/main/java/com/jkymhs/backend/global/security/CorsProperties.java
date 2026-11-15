package com.jkymhs.backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * application.yml의 cors.* 설정. 브라우저에서 이 API를 호출할 수 있는 FE 주소 목록이다.
 * 비어 있으면 어떤 Origin도 허용하지 않는다.
 *
 * 값이 틀리면 서버가 시작할 때 바로 실패한다. 브라우저가 보내는 Origin은 "https://whereto.app"처럼 스킴과 호스트(와 포트)뿐이다.
 * "whereto.app"이나 "https://whereto.app/path"는 서버가 떠도 어떤 요청과도 맞지 않아서,
 * 배포한 뒤에야 모든 브라우저 요청이 403이 되는 것을 보고 알게 된다.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @DefaultValue List<String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins.forEach(CorsProperties::validate);
    }

    private static void validate(String origin) {
        // 쿠키를 함께 보내는 CORS에서 *를 허용하면 아무 사이트나 로그인한 사용자의 쿠키로 API를 호출할 수 있다
        if (origin.contains("*")) {
            throw invalid(origin, "*를 쓸 수 없습니다. 쿠키를 함께 보내는 CORS라서 정확한 주소만 허용합니다");
        }
        URI uri = parse(origin);
        // "null"은 샌드박스 iframe 등이 보내는 Origin 값이다. 허용하면 그런 페이지가 쿠키를 들고 호출할 수 있어서 이 검사로 막는다
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
