package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.service.TripGroup;
import com.jkymhs.backend.domain.trip.service.TripSyncState;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.util.List;

public record TripSummaryResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TripGroup group,
        List<Region> regions,
        Companion companion,
        String originName,
        int planCount,
        ConfirmedPlan confirmedPlan,
        Visit visit,
        TripSyncState sync,
        String thumbnailUrl
) {
    public record Region(Long id, String name) {
    }

    public record ConfirmedPlan(Long id, String name, int placeCount) {
    }

    public record Visit(int checked, int total) {
    }
}
