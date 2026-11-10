package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.VisitCheckStatus;

public record VisitCheckSaveResponse(VisitCheckStatus status, int checked, int total) {
}
