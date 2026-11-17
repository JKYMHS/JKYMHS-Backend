package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/**
 * 행동 기록 전용 실행기. 조회·찜 API가 행동 기록·프로필 계산을 기다리지 않고 바로 응답하게 한다.
 * 테스트({@code async.enabled=false})에서는 호출한 스레드에서 바로 실행된다.
 */
@Configuration
@EnableConfigurationProperties(BehaviorProperties.class)
public class BehaviorConfig {

    public static final String EXECUTOR = "behaviorExecutor";

    @Bean(name = EXECUTOR)
    public TaskExecutor behaviorExecutor(ExecutorFactory executorFactory, BehaviorProperties properties) {
        BehaviorProperties.Executor executor = properties.executor();
        return executorFactory.create("behavior", executor.coreSize(), executor.maxSize(), executor.queueCapacity());
    }
}
