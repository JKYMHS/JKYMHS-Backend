package com.jkymhs.backend.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"*", "https://app.example.com,*", "https://*.example.com"})
    @DisplayName("Origin에 *가 있으면 서버가 시작되지 않는다")
    void wildcardFailsAtStartup(String origins) {
        contextRunner
                .withPropertyValues("cors.allowed-origins=" + origins)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("*를 쓸 수 없습니다"));
    }

    @Test
    @DisplayName("값이 비어 있으면(기본값) 허용 주소가 없는 빈 목록이다")
    void blankMeansNoOrigin() {
        contextRunner
                .withPropertyValues("cors.allowed-origins=")
                .run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins()).isEmpty());
    }

    @Test
    @DisplayName("설정이 아예 없어도 빈 목록으로 시작한다")
    void missingMeansNoOrigin() {
        contextRunner
                .run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins()).isEmpty());
    }

    @EnableConfigurationProperties(CorsProperties.class)
    static class PropertiesConfig {
    }
}
