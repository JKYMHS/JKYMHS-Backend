package com.jkymhs.backend.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class RestDayParserTest {

    @Test
    @DisplayName("매주 X요일, X요일은 그 요일들로 읽는다")
    void weeklyDays() {
        assertThat(RestDayParser.parse("매주 월요일")).containsExactly(DayOfWeek.MONDAY);
        assertThat(RestDayParser.parse("매주 토요일 / 일요일"))
                .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(RestDayParser.parse("화요일(공휴일인 경우 다음날), 1월 1일")).containsExactly(DayOfWeek.TUESDAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"연중무휴", "연중무휴 (설·추석 당일 휴무)", "없음", "휴무 없음"})
    @DisplayName("연중무휴·없음은 빈 집합(쉬는 날 없음)이다")
    void noRestDay(String raw) {
        assertThat(RestDayParser.parse(raw)).isEqualTo(EnumSet.noneOf(DayOfWeek.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"첫째·셋째 월요일", "격주 화요일", "설날 및 추석 당일"})
    @DisplayName("매주가 아니거나 요일이 없으면 null(모름)이다")
    void unknown(String raw) {
        assertThat(RestDayParser.parse(raw)).isNull();
    }

    @Test
    @DisplayName("컬럼 값은 요일 순 MON..SUN, 쉬는 날 없음은 NONE, 모름은 null로 오간다")
    void columnRoundTrip() {
        assertThat(RestDays.toColumn(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))).isEqualTo("MON,SUN");
        assertThat(RestDays.toColumn(EnumSet.noneOf(DayOfWeek.class))).isEqualTo("NONE");
        assertThat(RestDays.toColumn(null)).isNull();
        assertThat(RestDays.fromColumn("MON,SUN")).containsExactly(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
        assertThat(RestDays.fromColumn("NONE")).isEmpty();
        assertThat(RestDays.fromColumn(null)).isNull();
    }
}
