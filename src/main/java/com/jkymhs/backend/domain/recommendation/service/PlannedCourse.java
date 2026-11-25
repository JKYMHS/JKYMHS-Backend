package com.jkymhs.backend.domain.recommendation.service;

import java.time.LocalDate;
import java.util.List;

public record PlannedCourse(String title, List<Day> days) {
    public record Day(int dayNumber, LocalDate date, List<Place> places) {
    }

    public record Place(Candidate candidate, int stayMinutes, String reason) {
    }
}
