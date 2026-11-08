package com.jkymhs.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "오늘", "지금"을 정하는 시계. 서비스 코드는 {@code LocalDate.now()} 대신 {@code LocalDate.now(clock)}을 쓴다.
 *
 * 이유 1: 서버(컨테이너)의 기본 시간대가 UTC여도 축제 진행 상태·D-day·영업 중 여부가 한국 날짜 기준으로 계산되게 한다.
 * 이유 2: 테스트에서 시계를 고정·이동할 수 있게 한다 (테스트의 MutableClock이 이 빈을 대신한다).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }
}
