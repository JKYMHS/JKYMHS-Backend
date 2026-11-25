package com.jkymhs.backend.domain.content.service;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static String shortName(DayOfWeek day) {
        return day.name().substring(0, 3);
    }
}
