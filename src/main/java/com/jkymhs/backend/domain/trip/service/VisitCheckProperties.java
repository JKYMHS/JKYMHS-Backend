package com.jkymhs.backend.domain.trip.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "visit-check")
public record VisitCheckProperties(
        @DefaultValue("0 0 10 * * *") String mailCron,
        @DefaultValue("3") @Min(1) int dismissDays,
        @DefaultValue("7") @Min(1) int mailLookbackDays
) {
}
