package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 방문 확인 저장. 답한 장소만 보내도 된다 (부분 저장).
 * rating: 갔으면 1~5, 안 갔으면 null 또는 0 (FE는 0을 보낸다, §5 Q27). 범위 확인은 visited에 따라 달라서 서비스에서 한다.
 */
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
