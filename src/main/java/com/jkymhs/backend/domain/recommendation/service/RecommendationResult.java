package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.dto.response.CourseResponse;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record RecommendationResult(List<CourseResponse> courses, boolean indoorShortage) {
    public Optional<CourseResponse> course(String courseId) {
        return courses.stream().filter(c -> c.courseId().equals(courseId)).findFirst();
    }

    public Set<Long> contentIds() {
        return courses.stream()
                .flatMap(c -> c.days().stream())
                .flatMap(d -> d.items().stream())
                .filter(CourseResponse.PlaceItem.class::isInstance)
                .map(i -> ((CourseResponse.PlaceItem) i).contentId())
                .collect(Collectors.toSet());
    }
}
