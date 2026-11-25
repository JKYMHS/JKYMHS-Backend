package com.jkymhs.backend.domain.content.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

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
