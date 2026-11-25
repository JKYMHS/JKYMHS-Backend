package com.jkymhs.backend.domain.content.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

public final class OpenStatusCalculator {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private OpenStatusCalculator() {
    }

    public static OpenStatus of(ContentSnapshot snapshot, LocalDateTime nowKst, int soonMinutes) {
        return of(snapshot.restDays(), snapshot.openTime(), snapshot.closeTime(), nowKst, soonMinutes);
    }

    public static OpenStatus of(Set<DayOfWeek> restDays, LocalTime open, LocalTime close,
                                LocalDateTime nowKst, int soonMinutes) {
        if (restDays != null && restDays.contains(nowKst.getDayOfWeek())) {
            return OpenStatus.CLOSED;
        }
        if (open == null || close == null) {
            return null;
        }
        int openMinute = minuteOfDay(open);
        int closeMinute = minuteOfDay(close);
        int now = minuteOfDay(nowKst.toLocalTime());
        if (closeMinute <= openMinute) {
            closeMinute += MINUTES_PER_DAY;
            if (now < openMinute) {
                now += MINUTES_PER_DAY;
            }
        }
        if (now < openMinute || now >= closeMinute) {
            return OpenStatus.CLOSED;
        }
        if (now >= closeMinute - soonMinutes) {
            return OpenStatus.SOON;
        }
        return OpenStatus.OPEN;
    }

    private static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
