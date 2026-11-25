package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record VisitCheckSaveRequest(
        @NotEmpty(message = "방문 여부를 입력해주세요.")
        List<@NotNull(message = "방문 여부를 입력해주세요.") @Valid Item> items
) {
    public record Item(
            @NotNull(message = "장소를 입력해주세요.") Long itemId,
            @NotNull(message = "방문 여부를 선택해주세요.") Boolean visited,
            Integer rating
    ) {
    }
}
