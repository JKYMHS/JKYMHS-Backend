package com.jkymhs.backend.domain.content.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tour-api.import")
public record TourImportProperties(String csvDir) {

    public boolean enabled() {
        return csvDir != null && !csvDir.isBlank();
    }
}
