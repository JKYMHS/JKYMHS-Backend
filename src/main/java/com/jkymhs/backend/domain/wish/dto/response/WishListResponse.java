package com.jkymhs.backend.domain.wish.dto.response;

import java.util.List;

public record WishListResponse(
        List<WishItemResponse> items,
        String nextCursor,
        boolean hasNext,
        TabCounts counts
) {
    public record TabCounts(int festival, int place) {
    }
}
