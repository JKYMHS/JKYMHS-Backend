package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlanRenameRequest(
        @NotBlank(message = "일정안 이름을 입력해주세요.")
        @Size(max = 30, message = "일정안 이름은 30자까지 입력할 수 있습니다.") String name
) {
}
