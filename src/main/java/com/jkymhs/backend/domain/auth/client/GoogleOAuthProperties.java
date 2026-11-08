package com.jkymhs.backend.domain.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 google.* 설정.
 * 주소(tokenUri, userInfoUri)도 설정으로 빼서 테스트나 다른 환경에서 바꿔 끼울 수 있게 했다.
 */
@ConfigurationProperties(prefix = "google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String tokenUri,
        String userInfoUri
) {
}
