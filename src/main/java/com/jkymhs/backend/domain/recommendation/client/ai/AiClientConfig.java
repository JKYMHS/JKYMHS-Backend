package com.jkymhs.backend.domain.recommendation.client.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * {@code ai.provider} 값으로 AI 클라이언트 하나만 빈으로 만든다. 값이 없으면 mock이다.
 */
@Configuration
public class AiClientConfig {

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = AiProperties.MOCK, matchIfMissing = true)
    public AiCourseClient mockAiCourseClient() {
        return new MockAiCourseClient();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = AiProperties.HTTP)
    public AiCourseClient httpAiCourseClient(AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new HttpAiCourseClient(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
