package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

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
