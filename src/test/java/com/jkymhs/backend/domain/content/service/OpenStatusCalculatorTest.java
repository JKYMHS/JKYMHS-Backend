package com.jkymhs.backend.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenStatusCalculatorTest {

    // 2026-11-09은 월요일이다
    private static final LocalDateTime MONDAY_NOON = LocalDateTime.of(2026, 11, 9, 12, 0);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);
    private static final int SOON = 60;

    @Test
    @DisplayName("오늘이 쉬는 요일이면 운영 시간과 상관없이 closed다")
    void restDay() {
        assertThat(of(EnumSet.of(DayOfWeek.MONDAY), NINE, SIX_PM, MONDAY_NOON)).isEqualTo(OpenStatus.CLOSED);
        assertThat(of(EnumSet.of(DayOfWeek.MONDAY), null, null, MONDAY_NOON)).isEqualTo(OpenStatus.CLOSED);
    }

    @Test
    @DisplayName("여는·닫는 시각을 모르면 null이다 (쉬는 날 없음이어도)")
    void unknownHours() {
        assertThat(of(EnumSet.noneOf(DayOfWeek.class), null, SIX_PM, MONDAY_NOON)).isNull();
        assertThat(of(null, null, null, MONDAY_NOON)).isNull();
    }

    @Test
    @DisplayName("닫기 60분 전부터는 soon, 그 전은 open, 여는 시각 전과 닫는 시각부터는 closed다")
    void boundaries() {
        assertThat(of(null, NINE, SIX_PM, at(8, 59))).isEqualTo(OpenStatus.CLOSED);
        assertThat(of(null, NINE, SIX_PM, at(9, 0))).isEqualTo(OpenStatus.OPEN);
        assertThat(of(null, NINE, SIX_PM, at(16, 59))).isEqualTo(OpenStatus.OPEN);
        assertThat(of(null, NINE, SIX_PM, at(17, 0))).isEqualTo(OpenStatus.SOON);
        assertThat(of(null, NINE, SIX_PM, at(17, 59))).isEqualTo(OpenStatus.SOON);
        assertThat(of(null, NINE, SIX_PM, at(18, 0))).isEqualTo(OpenStatus.CLOSED);
    }

    @Test
    @DisplayName("밤을 넘기는 영업(18:00~02:00)은 새벽 1시도 영업 중이고 1시부터 soon이다")
    void overnight() {
        LocalTime two = LocalTime.of(2, 0);
        assertThat(of(null, SIX_PM, two, at(23, 0))).isEqualTo(OpenStatus.OPEN);
        assertThat(of(null, SIX_PM, two, at(0, 30))).isEqualTo(OpenStatus.OPEN);
        assertThat(of(null, SIX_PM, two, at(1, 30))).isEqualTo(OpenStatus.SOON);
        assertThat(of(null, SIX_PM, two, at(3, 0))).isEqualTo(OpenStatus.CLOSED);
        assertThat(of(null, SIX_PM, two, at(12, 0))).isEqualTo(OpenStatus.CLOSED);
    }

    private static LocalDateTime at(int hour, int minute) {
        return MONDAY_NOON.with(LocalTime.of(hour, minute));
    }

    private static OpenStatus of(Set<DayOfWeek> restDays, LocalTime open, LocalTime close, LocalDateTime now) {
        return OpenStatusCalculator.of(restDays, open, close, now, SOON);
    }
}
