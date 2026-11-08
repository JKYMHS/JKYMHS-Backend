package com.jkymhs.backend.global.config;

import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CommonConfigTest extends IntegrationTestSupport {

    @Autowired
    private Clock clock;

    @Autowired
    private ExecutorFactory executorFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("주입되는 Clock은 서버 시간대와 상관없이 Asia/Seoul이다")
    void clockIsKst() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("테스트에서는 async.enabled=false라 실행기가 호출한 스레드에서 바로 실행한다")
    void syncExecutorInTest() {
        assertThat(executorFactory.create("test", 1, 1, 1)).isInstanceOf(SyncTaskExecutor.class);
    }

    @Test
    @DisplayName("테스트에서는 batch.enabled=false라 스케줄러 설정이 등록되지 않는다")
    void schedulingDisabledInTest() {
        assertThat(applicationContext.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
    }

    @Test
    @DisplayName("config/application-test.yml과 인증 PR에서 복사한 application-test.yml이 함께 읽힌다")
    void bothTestYamlFilesLoaded() {
        assertThat(environment.getProperty("tour-api.retry-backoff")).isEqualTo("0ms");
        assertThat(environment.getProperty("jwt.access-token-expiration")).isEqualTo("30m");
    }
}
