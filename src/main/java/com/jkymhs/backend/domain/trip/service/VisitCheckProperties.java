package com.jkymhs.backend.domain.trip.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml {@code visit-check.*}.
 *
 * @param dismissDays      "나중에"를 처음 누르면 이 날 수 뒤에 다시 묻는다
 * @param mailLookbackDays 끝난 지 이 날 수 안의 여행만 메일을 보낸다
 */
@Validated
@ConfigurationProperties(prefix = "visit-check")
public record VisitCheckProperties(
        @DefaultValue("0 0 10 * * *") String mailCron,
        @DefaultValue("3") @Min(1) int dismissDays,
        @DefaultValue("7") @Min(1) int mailLookbackDays
) {
}
