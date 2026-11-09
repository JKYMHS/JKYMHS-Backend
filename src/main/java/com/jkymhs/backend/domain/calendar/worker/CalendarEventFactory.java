package com.jkymhs.backend.domain.calendar.worker;

import com.jkymhs.backend.domain.calendar.client.google.GoogleEvent;
import com.jkymhs.backend.domain.trip.service.AppProperties;
import com.jkymhs.backend.domain.trip.service.CalendarItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 일정 항목 → 구글 캘린더 일정 내용.
 *
 * - 제목 "[어디 갈래] {장소 이름}" (사용자 캘린더의 다른 일정과 구분되게)
 * - 장소 = 주소, 설명 = 메모 + 장소 상세 링크
 * - 시작 시각이 없으면 종일 일정. 끝 시각이 없으면 머무는 시간, 그것도 없으면 1시간짜리로 만든다
 */
@Component
@RequiredArgsConstructor
public class CalendarEventFactory {

    static final String SUMMARY_PREFIX = "[어디 갈래] ";
    private static final int DEFAULT_MINUTES = 60;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final AppProperties appProperties;

    public GoogleEvent build(CalendarItem item, String eventId) {
        StringBuilder description = new StringBuilder();
        if (StringUtils.hasText(item.memo())) {
            description.append(item.memo()).append('\n');
        }
        if (item.contentId() != null) {
            description.append(appProperties.frontendUrl("/places/" + item.contentId()));
        }
        String summary = SUMMARY_PREFIX + item.name();
        if (item.allDay()) {
            return new GoogleEvent(eventId, summary, item.address(), description.toString().strip(), true,
                    item.date().toString(), item.date().plusDays(1).toString());
        }
        LocalDateTime start = LocalDateTime.of(item.date(), item.startTime());
        LocalDateTime end;
        if (item.endTime() != null) {
            end = LocalDateTime.of(item.date(), item.endTime());
            if (!end.isAfter(start)) {
                // 22:00 ~ 01:00처럼 자정을 넘긴 일정
                end = end.plusDays(1);
            }
        } else {
            int minutes = item.stayMinutes() != null && item.stayMinutes() > 0 ? item.stayMinutes() : DEFAULT_MINUTES;
            end = start.plusMinutes(minutes);
        }
        return new GoogleEvent(eventId, summary, item.address(), description.toString().strip(), false,
                start.format(DATE_TIME), end.format(DATE_TIME));
    }
}
