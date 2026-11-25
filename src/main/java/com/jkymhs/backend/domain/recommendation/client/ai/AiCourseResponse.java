package com.jkymhs.backend.domain.recommendation.client.ai;

import java.util.List;

public record AiCourseResponse(List<Course> courses) {
    public record Course(String title, String summary, List<Day> days) {
    }

    public record Day(int dayNumber, List<Item> items) {
    }

    public record Item(Long contentId, Integer stayMinutes, String reason) {
    }
}
