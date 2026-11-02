package com.jkymhs.backend.domain.auth.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 구글 OAuth 호출용 RestClient를 만든다.
 * 스프링 부트 4는 restclient 스타터 없이는 RestClient.Builder를 자동으로 만들어주지 않아서 여기서 직접 만든다.
 */
@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthConfig {

    /*
     * 타임아웃이 없으면 구글이 응답하지 않을 때 요청 스레드가 하염없이 기다리고, 그런 요청이 쌓이면 서버 전체가 멈춘다.
     * 연결 3초, 응답 5초 안에 안 오면 포기하고 502를 준다.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public GoogleOAuthClient googleOAuthClient(GoogleOAuthProperties properties) {
        // RestClient가 기본으로 쓰는 JDK HttpClient에 타임아웃만 더한다
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return new GoogleOAuthClient(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
