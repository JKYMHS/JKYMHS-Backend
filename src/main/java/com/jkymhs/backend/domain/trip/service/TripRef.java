package com.jkymhs.backend.domain.trip.service;

import java.time.LocalDate;

public record TripRef(Long id, LocalDate startDate, LocalDate endDate, int days) {
}
