package com.jkymhs.backend.domain.popularity.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "popularity")
public record PopularityProperties(
        @DefaultValue("30d") @NotNull Duration window
) {
}
