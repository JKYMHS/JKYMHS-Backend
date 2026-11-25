package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlanCreateRequest(
        @Size(max = 30, message = "일정안 이름은 30자까지 입력할 수 있습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "일정안 이름을 입력해주세요.") String name
) {
}
