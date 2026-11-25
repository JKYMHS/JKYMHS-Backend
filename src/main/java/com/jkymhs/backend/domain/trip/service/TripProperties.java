package com.jkymhs.backend.domain.trip.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trip")
public record TripProperties(
        @DefaultValue("14") @Min(1) int maxDays,
        @DefaultValue("10") @Min(1) int maxPlans,
        @DefaultValue("30") @Min(1) int maxItems
) {
}
