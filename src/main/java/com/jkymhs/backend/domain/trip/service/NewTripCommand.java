package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.TripOrigin;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;

import java.time.LocalDate;
import java.util.List;

public record NewTripCommand(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        List<Long> regionIds,
        Companion companion,
        ChildAgeGroup childAgeGroup,
        TripOrigin origin
) {
}
