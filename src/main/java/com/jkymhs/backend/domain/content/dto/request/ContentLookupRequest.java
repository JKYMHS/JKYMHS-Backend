package com.jkymhs.backend.domain.content.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContentLookupRequest(
        @NotEmpty(message = "조회할 id를 1개 이상 보내주세요.")
        @Size(max = 50, message = "한 번에 50개까지 조회할 수 있습니다.") List<Long> ids
) {
}
