package com.jkymhs.backend.domain.content.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 배치 실패 알림. {@code batch.alert-webhook-url}이 있으면 {@code {"text": "..."}}를 POST한다.
 * 슬랙 Incoming Webhook과 디스코드(…/slack 주소)가 둘 다 이 형식을 받는다. 다른 배치(인기 점수, 방문 확인 메일)도 같이 쓴다.
 * 알림이 실패해도 배치 결과에는 영향을 주지 않는다 (로그만 남긴다).
 */
@Slf4j
public class BatchAlertNotifier {

    private final RestClient restClient;
    private final BatchProperties properties;

    public BatchAlertNotifier(RestClient.Builder restClientBuilder, BatchProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public void notifyFailure(String text) {
        log.error("[배치 실패] {}", text);
        if (!properties.hasAlertWebhook()) {
            return;
        }
        try {
            restClient.post()
                    .uri(properties.alertWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Batch alert webhook failed: {}", e.getMessage());
        }
    }
}
