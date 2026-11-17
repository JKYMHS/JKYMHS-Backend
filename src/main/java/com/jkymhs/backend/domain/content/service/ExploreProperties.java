package com.jkymhs.backend.domain.content.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml {@code explore.*} (탐색 목록·자동완성·상세의 화면 규칙 값).
 *
 * @param closingSoonMinutes 닫기 몇 분 전부터 "곧 마감(soon)"으로 보여줄지 (FE 문서에 없어서 60분으로 정했다)
 * @param pageSizeMax        목록 한 페이지 최대 개수
 */
@Validated
@ConfigurationProperties(prefix = "explore")
public record ExploreProperties(
        @DefaultValue("60") @Min(0) @Max(720) int closingSoonMinutes,
        @DefaultValue("50") @Min(1) int pageSizeMax
) {
}
