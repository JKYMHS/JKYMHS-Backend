package com.jkymhs.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 날짜·시각에 따라 결과가 달라지는 통합 테스트의 부모 클래스 (축제 진행 상태, 지난 여행, 배치 백오프 등).
 *
 * 시계를 바꾸는 테스트를 모두 이 클래스 하나로 모아서 스프링 컨텍스트가 두 개(IntegrationTestSupport, ClockTestSupport)만
 * 만들어지게 한다. 테스트 클래스마다 설정이 다르면 컨텍스트를 새로 띄워서 테스트가 느려진다.
 * 테스트가 끝날 때마다 시계를 기본값으로 되돌려서, 앞 테스트가 옮긴 시계가 다음 테스트에 남지 않게 한다.
 */
@Import(FixedClockConfig.class)
public abstract class ClockTestSupport extends IntegrationTestSupport {

    @Autowired
    protected MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }
}
