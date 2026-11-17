package com.jkymhs.backend.domain.content.service;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI 운영·공연 시간 원문(usetime, playtime)에서 시각을 뽑는다.
 *
 * 원문은 사람이 쓴 문장이라 형식이 제각각이다 ("09:00~18:00 (입장마감 17:00)", "9.11.(금) 13:00 ~ 21:00 9.12.(토) ...",
 * "상시 개방", "프로그램별 상이"). 모든 경우를 해석하려 하지 않고 다음만 읽는다. 나머지는 null(모름)로 둔다.
 * <ul>
 *   <li>처음 나오는 "HH:mm~HH:mm" → 여는 시각·닫는 시각 (날짜별로 다르면 첫날 기준). 닫는 시각이 여는 시각보다 이르면 밤을 넘기는 영업으로 본다</li>
 *   <li>"입장마감 HH:mm", "매표마감 HH:mm" → 마지막 입장 시각</li>
 * </ul>
 * 원문은 contents.operating_hours_raw/playtime_raw에 그대로 남겨서 상세 화면은 원문을 보여준다.
 */
public final class OperatingHoursParser {

    // 9:00 ~ 18:00, 09:00-18:00, 09:00∼18:00 (물결표가 여러 문자로 들어온다)
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

    /** 처음 나오는 "HH:mm" 하나 (축제 eventstarttime 등). 없으면 null. */
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

    // "24:00"은 LocalTime으로 표현할 수 없어서 하루의 마지막 분(23:59)으로 바꾼다. 25시·61분 같은 값은 시각이 아니라고 본다
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
