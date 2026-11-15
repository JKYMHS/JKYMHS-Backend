package com.jkymhs.backend.domain.recommendation.client.ai;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * AI 서버에 보내는 요청 (AI 담당 팀원에게 제안한 계약, §5 Q1). 시각은 "HH:mm", 날짜는 "YYYY-MM-DD"다.
 *
 * @param candidates 점수 높은 순. 꼭 포함할 장소는 required=true
 * @param violations 직전 응답이 어긴 규칙 (다시 요청할 때만). AI가 이걸 보고 고친다
 */
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

    /**
     * @param companion     alone/couple/friends/kids/parents
     * @param childAgeGroup INFANT/ELEMENTARY/TEEN 또는 null
     * @param pace          relaxed/packed
     * @param timeOfDay     early/morning/afternoon (둘째 날부터의 시작 시각)
     * @param origin        첫째 날 출발지 (계약 초안에 더한 값: 가까운 곳부터 고르려면 필요하다)
     */
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

    /**
     * @param category 소분류 이름 ("박물관")
     * @param io       INDOOR/OUTDOOR/MIXED 또는 null
     * @param restDays MON…SUN. 빈 목록 = 쉬는 날 없음, null = 모름
     */
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

        /** 축제면 그날 열리는지. 축제가 아니면 항상 true */
        public boolean runsOn(LocalDate date) {
            if (eventStartDate == null || eventEndDate == null) {
                return true;
            }
            return !date.isBefore(eventStartDate) && !date.isAfter(eventEndDate);
        }
    }

    /**
     * @param lunch        "11:30-13:30"
     * @param noPlaceAfter 아이와 가면 "20:00" (이 시각 넘어서 끝나는 장소 금지), 아니면 null
     */
    public record Rules(int courses, PlacesPerDay placesPerDay, String lunch, String dinner, String noPlaceAfter) {
    }

    public record PlacesPerDay(int min, int max) {
    }
}
