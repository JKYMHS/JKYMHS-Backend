package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.entity.MealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseTimelineTest {

    @Test
    @DisplayName("앞 장소가 11:30~12:30 사이에 끝나면 바로 점심 60분을 넣고, 다음 장소는 점심 뒤에 시작한다")
    void lunchAfterPlace() {
        List<CourseTimeline.Slot> slots = CourseTimeline.layout(LocalTime.of(10, 0), List.of(100, 60), List.of(10, 5));

        assertThat(slots).containsExactly(
                new CourseTimeline.PlaceSlot(0, 610, 710),
                new CourseTimeline.MealSlot(MealType.LUNCH, 710, 770),
                new CourseTimeline.PlaceSlot(1, 775, 835));
    }

    @Test
    @DisplayName("창이 열리기 전이지만 다음 장소를 다녀오면 12:30을 넘기면 11:30에 점심을 먼저 넣는다")
    void lunchBeforeLongPlace() {
        List<CourseTimeline.Slot> slots = CourseTimeline.layout(LocalTime.of(9, 0), List.of(120, 120), List.of(0, 10));

        assertThat(slots.get(1)).isEqualTo(new CourseTimeline.MealSlot(MealType.LUNCH, 690, 750));
        assertThat(slots.get(2)).isEqualTo(new CourseTimeline.PlaceSlot(1, 760, 880));
    }

    @Test
    @DisplayName("저녁은 17:30~18:30 사이에 끝나는 장소 뒤에 넣고, 하루가 마지막 장소 뒤 창 안이면 끝에 넣는다")
    void dinnerAtEnd() {
        List<CourseTimeline.Slot> slots = CourseTimeline.layout(LocalTime.of(15, 0), List.of(90, 60), List.of(0, 0));

        assertThat(slots).containsExactly(
                new CourseTimeline.PlaceSlot(0, 900, 990),
                new CourseTimeline.PlaceSlot(1, 990, 1050),
                new CourseTimeline.MealSlot(MealType.DINNER, 1050, 1110));
    }

    @Test
    @DisplayName("식사 창 전에 하루가 끝나면 식사 칸을 넣지 않는다")
    void noMealWhenDayEndsEarly() {
        List<CourseTimeline.Slot> slots = CourseTimeline.layout(LocalTime.of(9, 0), List.of(60), List.of(0));

        assertThat(slots).containsExactly(new CourseTimeline.PlaceSlot(0, 540, 600));
    }

    @Test
    @DisplayName("자정을 넘기는 시각도 돌아가지 않게 분으로 계산하고, 화면 시각은 23:59에서 멈춘다")
    void pastMidnight() {
        List<CourseTimeline.Slot> slots = CourseTimeline.layout(LocalTime.of(22, 0), List.of(180), List.of(0));

        assertThat(slots.getFirst().end()).isEqualTo(25 * 60);
        assertThat(CourseTimeline.toTime(25 * 60)).isEqualTo(LocalTime.of(23, 59));
    }
}
