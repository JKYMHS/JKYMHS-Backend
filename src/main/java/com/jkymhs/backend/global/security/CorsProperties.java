package com.jkymhs.backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * application.yml의 cors.* 설정. 브라우저에서 이 API를 호출할 수 있는 FE 주소 목록이다.
 * 비어 있으면 어떤 Origin도 허용하지 않는다.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @DefaultValue List<String> allowedOrigins
) {

    public CorsProperties {
        // 쿠키를 함께 보내는 CORS에서 *를 허용하면 아무 사이트나 로그인한 사용자의 쿠키로 API를 호출할 수 있다.
        // 스프링은 Origin이 붙은 요청이 들어올 때에야 예외를 던져서 배포한 뒤에야 알게 된다. 그래서 시작할 때 이유를 알려주고 막는다
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException(
                    "cors.allowed-origins에는 *를 쓸 수 없습니다. 쿠키를 함께 보내는 CORS라서 정확한 주소만 허용합니다: " + allowedOrigins);
        }
    }
}
