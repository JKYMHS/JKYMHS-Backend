package com.jkymhs.backend.domain.recommendation.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 검증을 통과한(또는 기본 코스로 만든) 코스. 아직 이동·시각은 없다.
 */
public record PlannedCourse(String title, List<Day> days) {

    public record Day(int dayNumber, LocalDate date, List<Place> places) {
    }

    public record Place(Candidate candidate, int stayMinutes, String reason) {
    }
}
