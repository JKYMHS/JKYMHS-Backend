package com.jkymhs.backend.domain.calendar.client.google;

import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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
