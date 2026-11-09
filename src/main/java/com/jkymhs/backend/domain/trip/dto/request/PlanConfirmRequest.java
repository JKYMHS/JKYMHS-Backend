package com.jkymhs.backend.domain.trip.dto.request;

import jakarta.validation.constraints.NotNull;

public record PlanConfirmRequest(@NotNull(message = "확정할 일정안을 선택해주세요.") Long planId) {
}
