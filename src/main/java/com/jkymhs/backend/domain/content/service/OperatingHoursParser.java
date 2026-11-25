package com.jkymhs.backend.domain.content.service;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OperatingHoursParser {

    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})\\s*:\\s*(\\d{2})\\s*[~∼～〜\\-]\\s*(\\d{1,2})\\s*:\\s*(\\d{2})");
    private static final Pattern LAST_ENTRY = Pattern.compile(
            "(?:입장|매표)\\s*마감[^0-9]{0,10}(\\d{1,2})\\s*:\\s*(\\d{2})");
    private static final Pattern SINGLE = Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{2})");

    private OperatingHoursParser() {
    }

    public static OperatingHours parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OperatingHours.UNKNOWN;
        }
        LocalTime open = null;
        LocalTime close = null;
        Matcher range = RANGE.matcher(raw);
        while (range.find()) {
            LocalTime from = time(range.group(1), range.group(2));
            LocalTime to = time(range.group(3), range.group(4));
            if (from != null && to != null) {
                open = from;
                close = to;
                break;
            }
        }
        LocalTime lastEntry = null;
        Matcher last = LAST_ENTRY.matcher(raw);
        if (last.find()) {
            lastEntry = time(last.group(1), last.group(2));
        }
        return new OperatingHours(open, close, lastEntry);
    }

    public static LocalTime firstTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = SINGLE.matcher(raw);
        while (matcher.find()) {
            LocalTime time = time(matcher.group(1), matcher.group(2));
            if (time != null) {
                return time;
            }
        }
        return null;
    }

    private static LocalTime time(String hour, String minute) {
        int h = Integer.parseInt(hour);
        int m = Integer.parseInt(minute);
        if (h == 24 && m == 0) {
            return LocalTime.of(23, 59);
        }
        if (h > 23 || m > 59) {
            return null;
        }
        return LocalTime.of(h, m);
    }
}
