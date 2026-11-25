package com.jkymhs.backend.domain.content.dto.response;

import java.util.List;

public record ContentLookupResponse(List<ContentItemResponse> items, List<Long> missingIds) {
}
