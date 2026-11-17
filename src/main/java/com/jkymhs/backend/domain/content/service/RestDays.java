package com.jkymhs.backend.domain.content.service;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * contents.rest_days 컬럼 값 ↔ 요일 집합. 컬럼은 "MON,TUE" (요일 순), 쉬는 날 없음은 "NONE", 모름은 NULL이다.
 * 빈 문자열 대신 "NONE"을 쓰는 이유: MySQL에서 빈 문자열과 NULL을 헷갈리지 않게 SQL로 볼 때도 뜻이 드러나게 했다.
 */
public final class RestDays {

    static final String NONE = "NONE";

    private RestDays() {
    }

    public static String toColumn(Set<DayOfWeek> days) {
        if (days == null) {
            return null;
        }
        if (days.isEmpty()) {
            return NONE;
        }
        return days.stream().sorted().map(RestDays::shortName).collect(Collectors.joining(","));
    }

    public static Set<DayOfWeek> fromColumn(String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (NONE.equals(column)) {
            return days;
        }
        for (String token : column.split(",")) {
            Arrays.stream(DayOfWeek.values())
                    .filter(day -> shortName(day).equals(token.trim()))
                    .findFirst()
                    .ifPresent(days::add);
        }
        return days;
    }

    /** FE 값 MON..SUN */
    public static String shortName(DayOfWeek day) {
        return day.name().substring(0, 3);
    }
}
