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

/**
 * AI 응답을 그대로 믿지 않고 확인한다 (작업 3단계 HOURS). 어긴 규칙을 문장으로 모아서 돌려주면, 작업이 그 목록을
 * {@code violations}로 붙여 AI에 한 번 더 요청한다.
 *
 * 확인하는 것: 코스 수·날짜 모양, 후보에 없는 id, 같은 장소 두 번, 빠진 꼭 포함 장소, 하루 장소 수, 축제 기간,
 * 쉬는 요일, 영업시간(직선거리 어림 이동으로 계산한 임시 시각 기준), 아이와 가면 20:00 넘어 끝나는 장소.
 * 꼭 포함할 장소는 사용자가 고른 곳이라 기간·요일·영업시간 검사에서 뺀다 (요청 때 경고로 이미 알렸다).
 */
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

    /**
     * 응답을 코스 계획으로 바꾼다. 검증을 통과한 응답이나 기본 코스에만 쓰지만, 혹시 섞인 모르는 id·중복은 조용히 뺀다.
     */
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

    // 직선거리 어림 이동으로 임시 시각을 매기고 영업시간·20:00 규칙을 본다 (실제 경로는 검증을 통과한 뒤에만 부른다)
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
        // 모르는 영업시간과 자정을 넘기는 영업(22:00~02:00)은 보지 않는다
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
