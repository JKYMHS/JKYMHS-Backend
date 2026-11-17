package com.jkymhs.backend.domain.content.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * application.yml {@code tour-api.*}. 값이 틀리면(한도 0, 타임아웃 없음) 서버가 뜰 때 바로 실패한다.
 * {@code tour-api.import.*}는 {@code import}가 자바 예약어라 record 필드로 쓸 수 없어서 {@code TourImportProperties}로 따로 읽는다.
 *
 * @param serviceKey 비어 있어도 서버는 뜬다. 동기화 배치만 경고를 남기고 건너뛴다
 */
@Validated
@ConfigurationProperties(prefix = "tour-api")
public record TourApiProperties(
        @NotBlank String baseUrl,
        String serviceKey,
        @Min(1) int dailyCallLimit,
        @NotNull Duration timeout,
        @DefaultValue("1s") @NotNull Duration retryBackoff,
        @Valid @DefaultValue Sync sync
) {

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /**
     * @param introPerRun 한 번 실행에 운영 정보(detailIntro2)를 새로 받을 콘텐츠 수
     */
    public record Sync(
            @DefaultValue("0 0 4 * * *") String cron,
            @DefaultValue("300") @Min(0) int introPerRun
    ) {
    }
}
