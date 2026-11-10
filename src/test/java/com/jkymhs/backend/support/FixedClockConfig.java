package com.jkymhs.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 서비스에 주입되는 {@code Clock}을 {@link MutableClock}으로 바꾼다.
 * 빈 이름을 운영 빈(clock)과 다르게 해서 덮어쓰기(bean overriding) 없이 {@code @Primary}로 우선 주입되게 한다.
 */
@TestConfiguration
public class FixedClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
