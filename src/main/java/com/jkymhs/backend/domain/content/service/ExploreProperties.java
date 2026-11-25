package com.jkymhs.backend.domain.content.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "explore")
public record ExploreProperties(
        @DefaultValue("60") @Min(0) @Max(720) int closingSoonMinutes,
        @DefaultValue("50") @Min(1) int pageSizeMax
) {
}
