package com.jkymhs.backend.domain.content.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * TourAPI 호출용 RestClient를 만든다 (스프링 부트 4는 restclient 스타터 없이는 RestClient.Builder를 자동으로 만들어주지 않는다).
 * 타임아웃이 없으면 TourAPI가 응답하지 않을 때 배치 스레드가 멈춘 채로 남는다.
 */
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
