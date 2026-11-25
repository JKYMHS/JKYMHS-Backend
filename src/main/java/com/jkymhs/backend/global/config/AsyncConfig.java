package com.jkymhs.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig {

    @Bean
    public ExecutorFactory executorFactory(AsyncProperties asyncProperties) {
        return new ExecutorFactory(asyncProperties.enabled());
    }
}
