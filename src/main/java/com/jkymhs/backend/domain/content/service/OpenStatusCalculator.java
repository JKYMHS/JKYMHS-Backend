package com.jkymhs.backend.domain.content.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * 장소의 현재 영업 상태를 계산한다. 저장하지 않고 조회할 때마다 계산한다 (시각에 따라 계속 바뀌는 값이다).
 *
 * 순서:
 * <ol>
 *   <li>오늘이 쉬는 요일이면 closed</li>
 *   <li>여는·닫는 시각을 모르면 null (FE는 배지를 숨긴다)</li>
 *   <li>[닫는 시각 − soonMinutes, 닫는 시각)이면 soon, [여는 시각, 닫는 시각 − soonMinutes)이면 open, 그 밖은 closed</li>
 * </ol>
 * 밤을 넘기는 영업(18:00~02:00)은 닫는 시각에 24시간을 더해서 비교한다. 새벽 01:00은 전날 영업의 연장으로 본다.
 */
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
