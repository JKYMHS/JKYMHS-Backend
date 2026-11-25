package com.jkymhs.backend.domain.trip.dto.response;

import java.time.LocalDate;

public record VisitCheckPendingResponse(Trip trip) {
    public record Trip(Long id, String title, LocalDate endDate) {
    }
}
