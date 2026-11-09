package com.jkymhs.backend.domain.calendar.service;

/**
 * 구글 캘린더 일정 id = "edgl" + 일정 항목 id.
 *
 * 구글은 일정 id를 만들 때 우리가 정한 값을 받는다 (base32hex 문자 a–v·0–9, 5~1024자). "edgl"은 모두 a–v 안의 문자다.
 * 항목 id로 정해 두면 같은 항목을 두 번 올려도(재시도·중복 클릭) 구글이 409로 막아서 일정이 두 개 생기지 않고,
 * 삭제 작업에서 일정 id만 보고 어느 항목의 일정인지 알 수 있다.
 */
public final class CalendarEventIds {

    private static final String PREFIX = "edgl";

    private CalendarEventIds() {
    }

    public static String of(Long planItemId) {
        return PREFIX + planItemId;
    }

    /** 우리가 만든 일정 id면 항목 id, 아니면 null */
    public static Long planItemIdOf(String eventId) {
        if (eventId == null || !eventId.startsWith(PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(eventId.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
