package com.jkymhs.backend.domain.content.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "batch")
public record BatchProperties(
        @DefaultValue("true") boolean enabled,
        String alertWebhookUrl
) {

    public boolean hasAlertWebhook() {
        return alertWebhookUrl != null && !alertWebhookUrl.isBlank();
    }
}
