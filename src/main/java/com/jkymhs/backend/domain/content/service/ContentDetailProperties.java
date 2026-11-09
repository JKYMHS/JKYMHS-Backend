package com.jkymhs.backend.domain.content.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code content-detail.*}.
 *
 * @param refreshAfter 상세(소개글·사진)를 받은 지 이만큼 지나면 다음 조회 때 TourAPI에서 다시 받는다
 * @param lockTtl      같은 콘텐츠를 동시에 여러 요청이 다시 받지 않게 거는 Redis 락의 유효 시간
 */
@Validated
@ConfigurationProperties(prefix = "content-detail")
public record ContentDetailProperties(
        @DefaultValue("7d") @NotNull Duration refreshAfter,
        @DefaultValue("10s") @NotNull Duration lockTtl
) {
}
