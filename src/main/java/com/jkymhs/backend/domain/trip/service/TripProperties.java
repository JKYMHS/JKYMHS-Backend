package com.jkymhs.backend.domain.trip.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml {@code trip.*}. 직접 만드는 여행 기간(14일), 일정안 수(10개), 일정안 장소 수(30개) 제한 (§5 Q5, Q6).
 */
@Validated
@ConfigurationProperties(prefix = "trip")
public record TripProperties(
        @DefaultValue("14") @Min(1) int maxDays,
        @DefaultValue("10") @Min(1) int maxPlans,
        @DefaultValue("30") @Min(1) int maxItems
) {
}
