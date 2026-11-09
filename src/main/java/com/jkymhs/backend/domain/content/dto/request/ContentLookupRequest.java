package com.jkymhs.backend.domain.content.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * GET /api/contents/lookup?ids=1,2,3 (쉼표로 이어 쓰거나 ids를 여러 번 보내도 된다).
 */
public record ContentLookupRequest(
        @NotEmpty(message = "조회할 id를 1개 이상 보내주세요.")
        @Size(max = 50, message = "한 번에 50개까지 조회할 수 있습니다.") List<Long> ids
) {
}
