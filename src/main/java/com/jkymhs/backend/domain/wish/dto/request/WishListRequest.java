package com.jkymhs.backend.domain.wish.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WishListRequest(
        @NotNull(message = "잘못된 필터 값입니다.") CategoryTab tab,
        Boolean hideEnded,
        Boolean hideClosedToday,
        String cursor,
        @Min(value = 1, message = "잘못된 필터 값입니다.") @Max(value = 50, message = "잘못된 필터 값입니다.") Integer size
) {
}
