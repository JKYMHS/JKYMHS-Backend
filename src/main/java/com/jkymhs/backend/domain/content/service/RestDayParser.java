package com.jkymhs.backend.domain.content.service;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI 쉬는 날 원문(restdate)에서 매주 쉬는 요일을 뽑는다.
 *
 * <ul>
 *   <li>"연중무휴", "없음" → 빈 집합 (쉬는 날 없음)</li>
 *   <li>"매주 월요일", "월요일(공휴일이면 다음날)", "매주 토요일 / 일요일" → 그 요일들</li>
 *   <li>"첫째·셋째 월요일", "격주 화요일"처럼 매주가 아닌 휴무, 요일이 없는 문장("설날 당일") → null (모름)</li>
 * </ul>
 * 결과가 빈 집합이면 "항상 연다", null이면 "모른다"라서 둘을 섞지 않는다 (탐색 "오늘 쉬는 곳 숨기기"가 모르는 곳까지 숨기지 않게).
 */
public final class RestDayParser {

    private static final Pattern DAY = Pattern.compile("([월화수목금토일])요일");
    private static final Set<String> NO_REST_WORDS = Set.of("없음", "휴무없음", "휴무일없음", "휴일없음");

    private RestDayParser() {
    }

    public static Set<DayOfWeek> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("\\s", "");
        if (compact.contains("연중무휴") || NO_REST_WORDS.contains(compact)) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        if (compact.contains("째") || compact.contains("격주")) {
            return null;
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Matcher matcher = DAY.matcher(compact);
        while (matcher.find()) {
            days.add(dayOf(matcher.group(1).charAt(0)));
        }
        return days.isEmpty() ? null : days;
    }

    private static DayOfWeek dayOf(char korean) {
        return switch (korean) {
            case '월' -> DayOfWeek.MONDAY;
            case '화' -> DayOfWeek.TUESDAY;
            case '수' -> DayOfWeek.WEDNESDAY;
            case '목' -> DayOfWeek.THURSDAY;
            case '금' -> DayOfWeek.FRIDAY;
            case '토' -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }
}
