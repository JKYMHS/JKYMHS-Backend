package com.jkymhs.backend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 배치(TourAPI 동기화, 인기 점수, 방문 확인 메일 등)를 켠다.
 *
 * {@code batch.enabled=false}이면 스케줄러 자체를 켜지 않는다. 테스트는 이 값을 끄고 배치 빈의 메서드를 직접 불러서
 * 정해진 시각을 기다리지 않고 결과를 확인한다. 서버를 여러 대 띄울 때 한 대만 배치를 돌리는 용도로도 쓸 수 있다.
 * 모든 {@code @Scheduled}는 {@code zone = "Asia/Seoul"}을 적어서 서버 시간대와 상관없이 한국 시각에 돈다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "batch.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
