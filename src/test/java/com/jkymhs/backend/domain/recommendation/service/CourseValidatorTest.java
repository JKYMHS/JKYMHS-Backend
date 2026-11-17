package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.RouteProperties;
import com.jkymhs.backend.domain.route.service.RouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 없이 검증 규칙만 본다. 이동 시간은 RouteService.estimate(직선거리 어림값)만 쓰므로 외부 클라이언트·Redis 없이 만든다.
 */
class CourseValidatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LatLng ORIGIN = new LatLng(37.5700, 126.9770);

    private final CourseValidator validator = new CourseValidator(new RouteService(null, null, null,
            new RouteProperties(Duration.ofDays(7), 1000, 500, 1.5, new RouteProperties.Executor(1, 1, 0)), null));

    @Test
    @DisplayName("규칙에 맞는 코스 3개는 위반이 없다")
    void valid() {
        Map<Long, Candidate> candidates = candidates(place(1, null, null, null), place(2, null, null, null),
                place(3, null, null, null));
        assertThat(validator.validate(threeOf(day(1, 1, 2, 3)), context(candidates, List.of(), false, 2, 3)))
                .isEmpty();
    }

    @Test
    @DisplayName("코스 수 부족·후보에 없는 id·중복·하루 장소 수·빠진 꼭 포함 장소를 모두 위반으로 모은다")
    void shapeViolations() {
        Map<Long, Candidate> candidates = candidates(place(1, null, null, null), place(2, null, null, null),
                required(place(3, null, null, null)));
        CourseContext context = context(candidates, List.of(3L), false, 2, 3);

        assertThat(validator.validate(new AiCourseResponse(List.of()), context))
                .containsExactly("코스는 3개가 필요합니다.");
        List<String> violations = validator.validate(threeOf(day(1, 1, 1, 99)), context);
        assertThat(violations).anyMatch(v -> v.contains("같은 장소가 두 번 들어갔습니다: 1"))
                .anyMatch(v -> v.contains("후보에 없는 장소입니다: 99"))
                .anyMatch(v -> v.contains("꼭 포함할 장소가 빠졌습니다: 3"));
        assertThat(validator.validate(threeOf(day(1, 1)), context))
                .anyMatch(v -> v.contains("1일차 장소는 2~3곳이어야 합니다 (1곳)."));
    }

    @Test
    @DisplayName("영업시간 밖·쉬는 요일·기간 밖 축제는 위반이지만, 꼭 포함할 장소는 보지 않는다")
    void hoursAndDates() {
        ContentSnapshot closedEarly = place(1, LocalTime.of(9, 0), LocalTime.of(10, 30), null);
        ContentSnapshot mondayOff = withRestDays(place(2, null, null, null), Set.of(DayOfWeek.MONDAY));
        ContentSnapshot oldFestival = festival(3, MONDAY.minusDays(10), MONDAY.minusDays(5));
        ContentSnapshot requiredFestival = festival(4, MONDAY.minusDays(10), MONDAY.minusDays(5));
        Map<Long, Candidate> candidates = candidates(closedEarly, mondayOff, oldFestival, required(requiredFestival));
        CourseContext context = context(candidates, List.of(4L), false, 1, 4);

        List<String> violations = validator.validate(threeOf(day(1, 1, 2, 3, 4)), context);

        assertThat(violations).anyMatch(v -> v.startsWith("코스 1: 1 방문 시각 10:") && v.endsWith("영업시간 09:00~10:30 밖입니다."))
                .anyMatch(v -> v.contains("2는 2026-11-09(MONDAY)에 쉽니다."))
                .anyMatch(v -> v.contains("축제 3는 2026-11-09에 열리지 않습니다."))
                .noneMatch(v -> v.contains(" 4"));
    }

    @Test
    @DisplayName("아이와 가면 20:00 넘어 끝나는 장소는 위반이다")
    void kidsEvening() {
        Map<Long, Candidate> candidates = candidates(place(1, null, null, null), place(2, null, null, null));
        CourseContext kids = new CourseContext(candidates, List.of(), MONDAY, 1, 1, 2, true, LocalTime.of(18, 0),
                LocalTime.of(10, 0), "출발지", ORIGIN, 3);

        assertThat(validator.validate(threeOf(day(1, 1, 2)), kids))
                .anyMatch(v -> v.contains("아이와 가는 일정은 20:00 전에 끝나야 합니다: 2"));
        CourseContext adults = new CourseContext(candidates, List.of(), MONDAY, 1, 1, 2, false, LocalTime.of(18, 0),
                LocalTime.of(10, 0), "출발지", ORIGIN, 3);
        assertThat(validator.validate(threeOf(day(1, 1, 2)), adults)).isEmpty();
    }

    // ---------- 도움 ----------

    private static CourseContext context(Map<Long, Candidate> candidates, List<Long> required, boolean kids, int min,
                                         int max) {
        return new CourseContext(candidates, required, MONDAY, 1, min, max, kids, LocalTime.of(10, 0),
                LocalTime.of(10, 0), "출발지", ORIGIN, 3);
    }

    private static AiCourseResponse threeOf(AiCourseResponse.Day day) {
        AiCourseResponse.Course course = new AiCourseResponse.Course("코스", null, List.of(day));
        return new AiCourseResponse(List.of(course, course, course));
    }

    private static AiCourseResponse.Day day(int dayNumber, long... ids) {
        return new AiCourseResponse.Day(dayNumber, java.util.Arrays.stream(ids)
                .mapToObj(id -> new AiCourseResponse.Item(id, 90, "이유")).toList());
    }

    private static Map<Long, Candidate> candidates(Object... items) {
        Map<Long, Candidate> map = new LinkedHashMap<>();
        for (Object item : items) {
            Candidate candidate = item instanceof Candidate c ? c : new Candidate((ContentSnapshot) item, 1.0, false);
            map.put(candidate.id(), candidate);
        }
        return map;
    }

    private static Candidate required(ContentSnapshot content) {
        return new Candidate(content, 1.0, true);
    }

    private static ContentSnapshot place(long id, LocalTime open, LocalTime close, LocalTime lastEntry) {
        return new ContentSnapshot(id, ContentType.ATTRACTION, "장소" + id, "주소", 37.5700 + id * 0.001, 126.9770,
                null, 1L, 2L, "서울", "종로구", "HS", "HS01", "HS010100", "궁", IoType.OUTDOOR, null, null, null, null,
                open, close, lastEntry, Set.of(), null, 0, 0, false);
    }

    private static ContentSnapshot withRestDays(ContentSnapshot c, Set<DayOfWeek> restDays) {
        return new ContentSnapshot(c.id(), c.contentType(), c.name(), c.address(), c.lat(), c.lng(), null, 1L, 2L,
                "서울", "종로구", "HS", "HS01", "HS010100", "궁", IoType.OUTDOOR, null, null, null, null, null, null, null,
                restDays, null, 0, 0, false);
    }

    private static ContentSnapshot festival(long id, LocalDate start, LocalDate end) {
        return new ContentSnapshot(id, ContentType.FESTIVAL, "축제" + id, "주소", 37.5700 + id * 0.001, 126.9770, null,
                1L, 2L, "서울", "종로구", "EV", "EV01", "EV010100", "축제", IoType.OUTDOOR, start, end, null, null, null,
                null, null, null, null, 0, 0, false);
    }
}
