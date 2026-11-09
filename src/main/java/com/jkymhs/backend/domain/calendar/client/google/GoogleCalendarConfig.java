package com.jkymhs.backend.domain.calendar.client.google;

import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 구글 캘린더 클라이언트와 작업 실행기.
 *
 * 실행기(calendarExecutor)를 따로 두는 이유: 작업 하나가 일정 50개를 차례로 올리면 수십 초가 걸릴 수 있다.
 * 스케줄러 스레드에서 직접 돌리면 그동안 다른 @Scheduled 배치가 밀리고, 다른 회원의 작업도 줄을 선다.
 */
@Configuration
@EnableConfigurationProperties(GoogleCalendarProperties.class)
public class GoogleCalendarConfig {

    @Bean
    public GoogleCalendarClient googleCalendarClient(GoogleCalendarProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new GoogleCalendarClient(RestClient.builder().requestFactory(requestFactory), properties);
    }

    @Bean
    public TaskExecutor calendarExecutor(ExecutorFactory factory, GoogleCalendarProperties properties) {
        GoogleCalendarProperties.Executor executor = properties.executor();
        return factory.create("calendar", executor.coreSize(), executor.maxSize(), executor.queueCapacity());
    }
}
