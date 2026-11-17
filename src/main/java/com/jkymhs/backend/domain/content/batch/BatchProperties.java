package com.jkymhs.backend.domain.content.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml {@code batch.*}.
 *
 * @param enabled         false면 @Scheduled 배치를 모두 끈다 (실제 스위치는 SchedulingConfig의 @ConditionalOnProperty가 같은 키를 읽는다)
 * @param alertWebhookUrl 배치 실패 시 {"text": …}를 POST할 주소. 비어 있으면 ERROR 로그만 남긴다
 */
@ConfigurationProperties(prefix = "batch")
public record BatchProperties(
        @DefaultValue("true") boolean enabled,
        String alertWebhookUrl
) {

    public boolean hasAlertWebhook() {
        return alertWebhookUrl != null && !alertWebhookUrl.isBlank();
    }
}
