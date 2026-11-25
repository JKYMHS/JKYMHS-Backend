package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.client.ai.AiProperties;
import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

@Configuration
@EnableConfigurationProperties({RecommendationProperties.class, AiProperties.class})
public class RecommendationConfig {
    public static final String EXECUTOR = "recommendationExecutor";

    @Bean(name = EXECUTOR)
    public TaskExecutor recommendationExecutor(ExecutorFactory factory, RecommendationProperties properties) {
        RecommendationProperties.Executor executor = properties.executor();
        return factory.create("recommendation", executor.coreSize(), executor.maxSize(), executor.queueCapacity());
    }
}
