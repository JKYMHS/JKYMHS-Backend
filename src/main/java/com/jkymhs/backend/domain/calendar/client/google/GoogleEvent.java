package com.jkymhs.backend.domain.calendar.client.google;

import java.util.LinkedHashMap;
import java.util.Map;

public record GoogleEvent(String id, String summary, String location, String description, boolean allDay,
                          String start, String end) {
    static final String TIME_ZONE = "Asia/Seoul";

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
