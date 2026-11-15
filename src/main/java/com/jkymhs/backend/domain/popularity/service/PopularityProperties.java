package com.jkymhs.backend.domain.popularity.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code popularity.*}. 실행 주기(cron)는 {@code @Scheduled}가 직접 읽는다.
 *
 * @param window 인기 점수에 넣을 최근 기간 (30일)
 */
@Validated
@ConfigurationProperties(prefix = "popularity")
public record PopularityProperties(
        @DefaultValue("30d") @NotNull Duration window
) {
}
