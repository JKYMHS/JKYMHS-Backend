package com.jkymhs.backend.domain.content.batch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({BatchProperties.class, TourImportProperties.class})
public class BatchConfig {

    // 알림 서버가 느려도 배치가 오래 붙잡히지 않게 짧게 둔다
    private static final Duration ALERT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public BatchAlertNotifier batchAlertNotifier(BatchProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(ALERT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(ALERT_TIMEOUT);
        return new BatchAlertNotifier(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
