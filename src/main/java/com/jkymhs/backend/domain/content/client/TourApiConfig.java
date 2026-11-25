package com.jkymhs.backend.domain.content.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(TourApiProperties.class)
public class TourApiConfig {

    @Bean
    public TourApiClient tourApiClient(TourApiProperties properties, StringRedisTemplate redisTemplate, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new TourApiClient(RestClient.builder().requestFactory(requestFactory), properties, redisTemplate, clock);
    }
}
