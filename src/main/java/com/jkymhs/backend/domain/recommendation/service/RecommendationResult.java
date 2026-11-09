package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.dto.response.CourseResponse;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis rec:result에 저장하는 추천 결과.
 *
 * @param indoorShortage 실내를 원했는데 코스의 실내 장소가 절반이 안 된다 (FE가 안내 문구를 띄운다)
 */
public record RecommendationResult(List<CourseResponse> courses, boolean indoorShortage) {

    public Optional<CourseResponse> course(String courseId) {
        return courses.stream().filter(c -> c.courseId().equals(courseId)).findFirst();
    }

    /** 결과에 들어간 장소 id 전부 (다시 추천할 때 점수를 깎을 곳) */
    public Set<Long> contentIds() {
        return courses.stream()
                .flatMap(c -> c.days().stream())
                .flatMap(d -> d.items().stream())
                .filter(CourseResponse.PlaceItem.class::isInstance)
                .map(i -> ((CourseResponse.PlaceItem) i).contentId())
                .collect(Collectors.toSet());
    }
}
