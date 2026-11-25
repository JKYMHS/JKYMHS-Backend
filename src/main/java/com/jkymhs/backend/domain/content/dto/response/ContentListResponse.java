package com.jkymhs.backend.domain.content.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ContentListResponse(
        List<ContentItemResponse> items,
        String nextCursor,
        boolean hasNext,
        TabCounts counts,
        LocalDate asOf
) {

    public record TabCounts(int festival, int place) {
    }
}
