package com.jkymhs.backend.domain.recommendation.client.ai;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AiCourseRequest(
        Long requestId,
        Conditions conditions,
        Profile profile,
        List<Candidate> candidates,
        Rules rules,
        List<String> violations
) {
    public AiCourseRequest withViolations(List<String> newViolations) {
        return new AiCourseRequest(requestId, conditions, profile, candidates, rules, List.copyOf(newViolations));
    }

    public record Conditions(
            LocalDate startDate,
            LocalDate endDate,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            String companion,
            String childAgeGroup,
            String pace,
            boolean indoor,
            String timeOfDay,
            Point origin
    ) {
        public int days() {
            return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        }
    }

    public record Point(double lat, double lng) {
    }

    public record Profile(List<TopCategory> topCategories) {
    }

    public record TopCategory(String lcls2, double score) {
    }

    public record Candidate(
            Long contentId,
            String name,
            double lat,
            double lng,
            String category,
            String io,
            @JsonFormat(pattern = "HH:mm") LocalTime openTime,
            @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
            @JsonFormat(pattern = "HH:mm") LocalTime lastEntryTime,
            List<String> restDays,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            boolean required
    ) {
        public boolean runsOn(LocalDate date) {
            if (eventStartDate == null || eventEndDate == null) {
                return true;
            }
            return !date.isBefore(eventStartDate) && !date.isAfter(eventEndDate);
        }
    }

    public record Rules(int courses, PlacesPerDay placesPerDay, String lunch, String dinner, String noPlaceAfter) {
    }

    public record PlacesPerDay(int min, int max) {
    }
}
