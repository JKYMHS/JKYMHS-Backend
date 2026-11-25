package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.VisitCheckStatus;

import java.time.LocalDate;

public record VisitCheckDismissResponse(VisitCheckStatus status, LocalDate nextShowAt) {
}
