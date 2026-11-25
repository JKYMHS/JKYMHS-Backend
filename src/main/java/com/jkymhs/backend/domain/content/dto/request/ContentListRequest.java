package com.jkymhs.backend.domain.content.dto.request;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.content.service.ContentChip;
import com.jkymhs.backend.domain.content.service.ContentSort;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ContentListRequest(
        @NotNull(message = "잘못된 필터 값입니다.") CategoryTab tab,
        String q,
        Long regionId,
        Long categoryId,
        List<FestivalStatus> status,
        ContentSort sort,
        ContentChip chip,
        String bbox,
        String cursor,
        @Min(value = 1, message = "잘못된 필터 값입니다.") Integer size
) {
}
