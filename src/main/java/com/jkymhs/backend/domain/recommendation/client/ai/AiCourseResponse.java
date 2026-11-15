package com.jkymhs.backend.domain.recommendation.client.ai;

import java.util.List;

/**
 * AI 서버 응답. 서버가 그대로 믿지 않고 CourseValidator로 확인한다 (없는 id, 빠진 필수 장소, 영업시간 등).
 */
public record AiCourseResponse(List<Course> courses) {

    public record Course(String title, String summary, List<Day> days) {
    }

    public record Day(int dayNumber, List<Item> items) {
    }

    /**
     * @param stayMinutes 머무는 시간(분). 비었거나 이상한 값이면 서버가 기본값(90분)으로 바꾼다
     * @param reason      화면에 보여줄 추천 이유
     */
    public record Item(Long contentId, Integer stayMinutes, String reason) {
    }
}
