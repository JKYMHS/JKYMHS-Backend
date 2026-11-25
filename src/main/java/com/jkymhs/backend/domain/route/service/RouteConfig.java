package com.jkymhs.backend.domain.route.service;

import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityClient;
import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityProperties;
import com.jkymhs.backend.domain.route.client.odsay.OdsayClient;
import com.jkymhs.backend.domain.route.client.odsay.OdsayProperties;
import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({RouteProperties.class, OdsayProperties.class, KakaoMobilityProperties.class})
public class RouteConfig {
    @Bean
    public OdsayClient odsayClient(OdsayProperties properties) {
        return new OdsayClient(builder(properties.timeout()), properties);
    }

    @Bean
    public KakaoMobilityClient kakaoMobilityClient(KakaoMobilityProperties properties) {
        return new KakaoMobilityClient(builder(properties.timeout()), properties);
    }

    @Bean
    public TaskExecutor routeExecutor(ExecutorFactory factory, RouteProperties properties) {
        RouteProperties.Executor executor = properties.executor();
        return factory.create("route", executor.coreSize(), executor.maxSize(), executor.queueCapacity());
    }

    private static RestClient.Builder builder(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
