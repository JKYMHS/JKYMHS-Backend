package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.client.ai.AiProperties;
import com.jkymhs.backend.global.config.ExecutorFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/**
 * 추천 작업 실행기. 요청 API는 행만 만들고 202로 바로 답하고, 실제 작업(후보 → AI → 검증 → 경로 → 식사)은 이 실행기에서 돈다.
 * 대기열(50)까지 가득 차면 요청을 FAILED로 두고 503을 준다 (무한정 쌓아 두면 사용자가 30초 넘게 기다리게 된다).
 */
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
