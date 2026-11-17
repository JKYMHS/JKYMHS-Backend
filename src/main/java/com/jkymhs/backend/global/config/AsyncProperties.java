package com.jkymhs.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled false이면 {@link ExecutorFactory}가 호출한 스레드에서 바로 실행하는 실행기를 만든다 (테스트용)
 */
@ConfigurationProperties(prefix = "async")
public record AsyncProperties(@DefaultValue("true") boolean enabled) {
}
