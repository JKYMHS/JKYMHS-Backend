package com.jkymhs.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async}를 켜고, 도메인마다 자기 실행기를 만들 때 쓰는 {@link ExecutorFactory}를 등록한다.
 *
 * 실행기를 하나로 같이 쓰지 않는 이유: AI 추천처럼 오래 걸리는 작업이 실행기를 다 차지하면
 * 행동 데이터 기록 같은 짧은 작업까지 밀린다. 도메인별로 나누면 한쪽이 막혀도 다른 쪽은 돈다.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig {

    @Bean
    public ExecutorFactory executorFactory(AsyncProperties asyncProperties) {
        return new ExecutorFactory(asyncProperties.enabled());
    }
}
