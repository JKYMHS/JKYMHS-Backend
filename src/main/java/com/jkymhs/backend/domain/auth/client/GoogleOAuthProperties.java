package com.jkymhs.backend.domain.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String tokenUri,
        String userInfoUri
) {
}
