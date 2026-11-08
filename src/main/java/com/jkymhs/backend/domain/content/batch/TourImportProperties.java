package com.jkymhs.backend.domain.content.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml {@code tour-api.import.*}.
 *
 * @param csvDir tools/tourapi/collect.py export 결과 폴더. 비어 있으면 서버 시작 시 CSV 적재를 건너뛴다
 */
@ConfigurationProperties(prefix = "tour-api.import")
public record TourImportProperties(String csvDir) {

    public boolean enabled() {
        return csvDir != null && !csvDir.isBlank();
    }
}
