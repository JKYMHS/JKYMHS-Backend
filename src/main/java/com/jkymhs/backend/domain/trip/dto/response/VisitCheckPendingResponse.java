package com.jkymhs.backend.domain.trip.dto.response;

import java.time.LocalDate;

/**
 * @param trip 배너에 띄울 여행. 없으면 null
 */
public record VisitCheckPendingResponse(Trip trip) {

    public record Trip(Long id, String title, LocalDate endDate) {
    }
}
