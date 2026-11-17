package com.jkymhs.backend.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClockTestSupportTest extends ClockTestSupport {

    @Autowired
    private Clock injectedClock;

    @Test
    @DisplayName("서비스에 주입되는 Clock은 고정 시계이고 기본값은 2026-11-09(월) 10:00 KST다")
    void defaultNow() {
        assertThat(injectedClock).isSameAs(clock);
        assertThat(LocalDateTime.now(injectedClock)).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 0));
        assertThat(LocalDate.now(injectedClock).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("시계를 지정하거나 옮기면 주입된 Clock의 현재 시각도 바뀐다")
    void setAndAdvance() {
        clock.setNow(LocalDateTime.of(2026, 12, 31, 23, 50));
        clock.advance(Duration.ofMinutes(20));

        assertThat(LocalDateTime.now(injectedClock)).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 10));
    }
}
