package com.jkymhs.backend.domain.place.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 카카오 로컬 API용 RestClient. 사용자가 화면에서 기다리는 요청이라 타임아웃을 3초로 짧게 둔다.
 */
@Configuration
@EnableConfigurationProperties(KakaoLocalProperties.class)
public class KakaoLocalConfig {

    @Bean
    public KakaoLocalClient kakaoLocalClient(KakaoLocalProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new KakaoLocalClient(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
