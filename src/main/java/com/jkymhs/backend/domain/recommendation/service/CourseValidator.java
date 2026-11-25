package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CourseValidator {
    static final int MIN_STAY_MINUTES = 30;
    static final int MAX_STAY_MINUTES = 300;
    static final int DEFAULT_STAY_MINUTES = 90;
    static final int KIDS_LAST_END_MINUTES = 20 * 60;

    private final RouteService routeService;

    public List<String> validate(AiCourseResponse response, CourseContext context) {
        List<String> violations = new ArrayList<>();
        if (response == null || response.courses() == null || response.courses().size() < context.courses()) {
            violations.add("코스는 " + context.courses() + "개가 필요합니다.");
            return violations;
        }
        for (int c = 0; c < context.courses(); c++) {
            validateCourse(c + 1, response.courses().get(c), context, violations);
        }
        return violations;
    }

    public List<PlannedCourse> toPlans(AiCourseResponse response, CourseContext context) {
        List<PlannedCourse> plans = new ArrayList<>();
        for (AiCourseResponse.Course course : response.courses().stream().limit(context.courses()).toList()) {
            Set<Long> seen = new HashSet<>();
            List<PlannedCourse.Day> days = new ArrayList<>();
            for (int dayNumber = 1; dayNumber <= context.days(); dayNumber++) {
                AiCourseResponse.Day day = dayOf(course, dayNumber);
                List<PlannedCourse.Place> places = new ArrayList<>();
                if (day != null && day.items() != null) {
                    for (AiCourseResponse.Item item : day.items()) {
                        Candidate candidate = item == null ? null : context.candidates().get(item.contentId());
                        if (candidate != null && seen.add(candidate.id())) {
                            places.add(new PlannedCourse.Place(candidate, stayOf(item.stayMinutes()), item.reason()));
                        }
                    }
                }
                days.add(new PlannedCourse.Day(dayNumber, context.startDate().plusDays(dayNumber - 1), places));
            }
            plans.add(new PlannedCourse(course.title(), days));
        }
        return plans;
    }

    static int stayOf(Integer stayMinutes) {
        if (stayMinutes == null || stayMinutes < MIN_STAY_MINUTES || stayMinutes > MAX_STAY_MINUTES) {
            return DEFAULT_STAY_MINUTES;
        }
        return stayMinutes;
    }

    private void validateCourse(int courseNumber, AiCourseResponse.Course course, CourseContext context,
                                List<String> violations) {
        String prefix = "코스 " + courseNumber + ": ";
        if (course == null || course.days() == null) {
            violations.add(prefix + "days가 없습니다.");
            return;
        }
        Set<Integer> dayNumbers = new HashSet<>();
        for (AiCourseResponse.Day day : course.days()) {
            if (day == null || day.dayNumber() < 1 || day.dayNumber() > context.days() || !dayNumbers.add(day.dayNumber())) {
                violations.add(prefix + "dayNumber는 1~" + context.days() + "이 한 번씩 있어야 합니다.");
                return;
            }
        }
        if (dayNumbers.size() != context.days()) {
            violations.add(prefix + "날짜 " + context.days() + "일이 모두 있어야 합니다.");
            return;
        }

        Set<Long> seen = new HashSet<>();
        LatLng previousLastPlace = context.origin();
        for (AiCourseResponse.Day day : course.days().stream()
                .sorted(Comparator.comparingInt(AiCourseResponse.Day::dayNumber)).toList()) {
            LocalDate date = context.startDate().plusDays(day.dayNumber() - 1);
            List<AiCourseResponse.Item> items = day.items() == null ? List.of() : day.items();
            if (items.size() < context.minPerDay() || items.size() > context.maxPerDay()) {
                violations.add(prefix + day.dayNumber() + "일차 장소는 " + context.minPerDay() + "~"
                        + context.maxPerDay() + "곳이어야 합니다 (" + items.size() + "곳).");
            }
            List<Candidate> places = new ArrayList<>();
            List<Integer> stays = new ArrayList<>();
            for (AiCourseResponse.Item item : items) {
                Candidate candidate = item == null ? null : context.candidates().get(item.contentId());
                if (candidate == null) {
                    violations.add(prefix + "후보에 없는 장소입니다: " + (item == null ? null : item.contentId()));
                    continue;
                }
                if (!seen.add(candidate.id())) {
                    violations.add(prefix + "같은 장소가 두 번 들어갔습니다: " + candidate.id());
                    continue;
                }
                places.add(candidate);
                stays.add(stayOf(item.stayMinutes()));
                if (!candidate.required()) {
                    checkDate(prefix, candidate, date, violations);
                }
            }
            checkHours(prefix, day.dayNumber(), places, stays, previousLastPlace, context, violations);
            if (!places.isEmpty()) {
                ContentSnapshot last = places.getLast().content();
                previousLastPlace = new LatLng(last.lat(), last.lng());
            }
        }
        for (Long requiredId : context.requiredIds()) {
            if (context.candidates().containsKey(requiredId) && !seen.contains(requiredId)) {
                violations.add(prefix + "꼭 포함할 장소가 빠졌습니다: " + requiredId);
            }
        }
    }

    private static void checkDate(String prefix, Candidate candidate, LocalDate date, List<String> violations) {
        if (!candidate.runsOn(date)) {
            violations.add(prefix + "축제 " + candidate.id() + "는 " + date + "에 열리지 않습니다.");
        }
        if (candidate.content().restDays() != null && candidate.content().restDays().contains(date.getDayOfWeek())) {
            violations.add(prefix + candidate.id() + "는 " + date + "(" + date.getDayOfWeek() + ")에 쉽니다.");
        }
    }

    private void checkHours(String prefix, int dayNumber, List<Candidate> places, List<Integer> stays, LatLng dayOrigin,
                            CourseContext context, List<String> violations) {
        List<Integer> moves = new ArrayList<>();
        LatLng from = dayOrigin;
        for (Candidate place : places) {
            LatLng to = new LatLng(place.content().lat(), place.content().lng());
            moves.add(from == null ? 0 : routeService.estimate(from, to).minutes());
            from = to;
        }
        for (CourseTimeline.Slot slot : CourseTimeline.layout(context.dayStart(dayNumber), stays, moves)) {
            if (!(slot instanceof CourseTimeline.PlaceSlot placeSlot)) {
                continue;
            }
            Candidate candidate = places.get(placeSlot.index());
            if (context.kids() && placeSlot.end() > KIDS_LAST_END_MINUTES) {
                violations.add(prefix + "아이와 가는 일정은 20:00 전에 끝나야 합니다: " + candidate.id());
            }
            if (!candidate.required()) {
                checkOpen(prefix, candidate, placeSlot, violations);
            }
        }
    }

    private static void checkOpen(String prefix, Candidate candidate, CourseTimeline.PlaceSlot slot,
                                  List<String> violations) {
        ContentSnapshot content = candidate.content();
        LocalTime open = candidate.isFestival() ? content.eventStartTime() : content.openTime();
        LocalTime close = candidate.isFestival() ? content.eventEndTime() : content.closeTime();
        if (open == null || close == null || !close.isAfter(open)) {
            return;
        }
        int openMinutes = CourseTimeline.toMinutes(open);
        int closeMinutes = CourseTimeline.toMinutes(close);
        LocalTime lastEntry = content.lastEntryTime();
        int lastEntryMinutes = lastEntry == null ? closeMinutes : CourseTimeline.toMinutes(lastEntry);
        if (slot.start() < openMinutes || slot.start() > lastEntryMinutes || slot.end() > closeMinutes) {
            violations.add(prefix + candidate.id() + " 방문 시각 " + CourseTimeline.toTime(slot.start()) + "~"
                    + CourseTimeline.toTime(slot.end()) + "이 영업시간 " + open + "~" + close + " 밖입니다.");
        }
    }

    private static AiCourseResponse.Day dayOf(AiCourseResponse.Course course, int dayNumber) {
        if (course.days() == null) {
            return null;
        }
        return course.days().stream().filter(d -> d != null && d.dayNumber() == dayNumber).findFirst().orElse(null);
    }
}
