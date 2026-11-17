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

/**
 * 경로 계산용 외부 클라이언트(ODsay, 카카오모빌리티)와 두 API를 동시에 부르는 실행기.
 * 사용자가 화면에서 기다리는 요청이라 타임아웃을 3초로 짧게 둔다 (넘으면 직선거리 어림값으로 답한다).
 */
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
