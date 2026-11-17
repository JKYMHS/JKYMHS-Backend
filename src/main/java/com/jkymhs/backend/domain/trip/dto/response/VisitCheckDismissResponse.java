package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.VisitCheckStatus;

import java.time.LocalDate;

/**
 * @param nextShowAt 다시 물을 날. 더 묻지 않으면(DISMISSED) null
 */
public record VisitCheckDismissResponse(VisitCheckStatus status, LocalDate nextShowAt) {
}
