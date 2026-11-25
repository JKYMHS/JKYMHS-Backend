package com.jkymhs.backend.domain.content.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "content-detail")
public record ContentDetailProperties(
        @DefaultValue("7d") @NotNull Duration refreshAfter,
        @DefaultValue("10s") @NotNull Duration lockTtl
) {
}
