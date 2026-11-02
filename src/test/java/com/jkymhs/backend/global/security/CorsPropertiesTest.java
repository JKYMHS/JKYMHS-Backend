package com.jkymhs.backend.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "null                     | http:// 또는 https://",
            "whereto.app              | http:// 또는 https://",
            "ftp://whereto.app        | http:// 또는 https://",
            "https://                 | 주소 형식",
            "https://whereto.app/     | 경로나 끝의 /",
            "https://whereto.app/path | 경로나 끝의 /",
            "https://whereto.app?a=1  | ?나 #",
            "https://whereto.app#top  | ?나 #",
            "https://me@whereto.app   | 사용자 정보",
            "http://                  | 주소 형식",
            "https:whereto.app        | 호스트가 없습니다"
    })
    @DisplayName("Origin 형식이 아니면 서버가 시작되지 않고, 메시지로 무엇이 틀렸는지 알려준다")
    void invalidOriginFailsAtStartup(String origin, String reason) {
        contextRunner
                .withPropertyValues("cors.allowed-origins=" + origin)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining(origin)
                        .hasMessageContaining(reason));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"https://whereto.app", "http://localhost:3000", "https://app.whereto.app:8443", "http://[::1]:3000"})
    @DisplayName("스킴과 호스트(포트 포함)만 있는 주소는 시작할 수 있다")
    void validOriginStarts(String origin) {
        contextRunner
                .withPropertyValues("cors.allowed-origins=" + origin)
                .run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins()).containsExactly(origin));
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
