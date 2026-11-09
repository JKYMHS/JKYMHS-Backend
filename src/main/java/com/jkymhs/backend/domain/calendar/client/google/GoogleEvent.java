package com.jkymhs.backend.domain.calendar.client.google;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 구글 캘린더에 보낼 일정 내용. 날짜·시각은 문자열로 미리 만들어 둔다
 * (Jackson 설정에 따라 LocalDateTime이 배열이나 다른 형식으로 나가지 않게).
 *
 * @param start/end 시각 일정이면 "2026-11-10T09:00:00", 종일 일정이면 "2026-11-10" (종일 일정의 end는 다음 날 — 구글 규칙)
 */
public record GoogleEvent(String id, String summary, String location, String description, boolean allDay,
                          String start, String end) {

    static final String TIME_ZONE = "Asia/Seoul";

    /**
     * 요청 본문. 시각 일정에서는 date를, 종일 일정에서는 dateTime을 명시적으로 null로 보낸다.
     * 시각 일정을 종일 일정으로 바꿀 때(PATCH) 예전 dateTime이 남아 있으면 구글이 두 값이 섞였다고 거절하기 때문이다.
     */
    Map<String, Object> body(boolean includeId, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (includeId) {
            body.put("id", id);
        }
        body.put("summary", summary);
        body.put("location", location);
        body.put("description", description);
        body.put("start", time(start));
        body.put("end", time(end));
        if (status != null) {
            body.put("status", status);
        }
        return body;
    }

    private Map<String, Object> time(String value) {
        Map<String, Object> time = new LinkedHashMap<>();
        if (allDay) {
            time.put("date", value);
            time.put("dateTime", null);
            time.put("timeZone", null);
        } else {
            time.put("date", null);
            time.put("dateTime", value);
            time.put("timeZone", TIME_ZONE);
        }
        return time;
    }
}
