package com.jkymhs.backend.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class OperatingHoursParserTest {

    @Test
    @DisplayName("처음 나오는 HH:mm~HH:mm을 여는·닫는 시각으로 읽는다 (공백·한 자리 시도 읽는다)")
    void parseRange() {
        assertThat(OperatingHoursParser.parse("09:00~18:00"))
                .isEqualTo(new OperatingHours(LocalTime.of(9, 0), LocalTime.of(18, 0), null));
        assertThat(OperatingHoursParser.parse("9:30 ~ 17:00 (월요일 휴관)"))
                .isEqualTo(new OperatingHours(LocalTime.of(9, 30), LocalTime.of(17, 0), null));
        assertThat(OperatingHoursParser.parse("9.11.(금) 13:00 ~ 21:00 9.12.(토) 10:00 ~ 22:00"))
                .isEqualTo(new OperatingHours(LocalTime.of(13, 0), LocalTime.of(21, 0), null));
    }

    @Test
    @DisplayName("입장마감·매표마감 시각을 마지막 입장 시각으로 읽는다")
    void parseLastEntry() {
        assertThat(OperatingHoursParser.parse("09:00~18:00 (입장마감 17:00)").lastEntry()).isEqualTo(LocalTime.of(17, 0));
        assertThat(OperatingHoursParser.parse("10:00-18:00 / 매표 마감 : 17:30").lastEntry()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    @DisplayName("밤을 넘기는 영업은 닫는 시각이 여는 시각보다 이른 채로 두고, 24:00은 23:59로 읽는다")
    void overnightAndMidnight() {
        assertThat(OperatingHoursParser.parse("18:00~02:00"))
                .isEqualTo(new OperatingHours(LocalTime.of(18, 0), LocalTime.of(2, 0), null));
        assertThat(OperatingHoursParser.parse("00:00~24:00").close()).isEqualTo(LocalTime.of(23, 59));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"상시 개방", "프로그램별 상이", "25:00~26:00"})
    @DisplayName("시각을 읽을 수 없으면 모두 null이다")
    void unknown(String raw) {
        assertThat(OperatingHoursParser.parse(raw)).isEqualTo(OperatingHours.UNKNOWN);
    }

    @Test
    @DisplayName("시각 하나만 있는 값은 firstTime으로 읽는다")
    void firstTime() {
        assertThat(OperatingHoursParser.firstTime("오후 7:30 시작")).isEqualTo(LocalTime.of(7, 30));
        assertThat(OperatingHoursParser.firstTime("미정")).isNull();
    }
}
