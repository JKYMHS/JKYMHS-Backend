package com.jkymhs.backend.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FestivalStatusTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 10);
    private static final LocalDate END = LocalDate.of(2026, 11, 12);

    @Test
    @DisplayName("시작 전은 UPCOMING, 시작일~종료일(당일 포함)은 ONGOING, 종료 다음 날부터 ENDED다")
    void status() {
        assertThat(FestivalStatus.of(START, END, START.minusDays(1))).isEqualTo(FestivalStatus.UPCOMING);
        assertThat(FestivalStatus.of(START, END, START)).isEqualTo(FestivalStatus.ONGOING);
        assertThat(FestivalStatus.of(START, END, END)).isEqualTo(FestivalStatus.ONGOING);
        assertThat(FestivalStatus.of(START, END, END.plusDays(1))).isEqualTo(FestivalStatus.ENDED);
    }

    @Test
    @DisplayName("날짜가 없으면 null이다")
    void missingDates() {
        assertThat(FestivalStatus.of(null, END, START)).isNull();
        assertThat(FestivalStatus.of(START, null, START)).isNull();
    }
}
