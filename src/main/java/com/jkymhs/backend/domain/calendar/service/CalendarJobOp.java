package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.entity.CalendarJobOperation;
import com.jkymhs.backend.domain.trip.event.CalendarEventRef;
import com.jkymhs.backend.domain.trip.service.CalendarItem;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 작업에 넣을 일 하나 (작업 항목 행이 되기 전 값).
 */
public record CalendarJobOp(Long planItemId, Long tripId, CalendarJobOperation operation, String eventId,
                            String calendarId, String itemName, String startLabel) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int NAME_MAX_LENGTH = 200;

    /**
     * 항목을 calendarId 캘린더와 같게 만드는 일: 올린 적 없으면 CREATE, 다른 캘린더에 있으면 MOVE, 같은 캘린더면 UPDATE.
     */
    public static CalendarJobOp upsert(CalendarItem item, String calendarId) {
        CalendarJobOperation operation;
        if (!item.hasEvent()) {
            operation = CalendarJobOperation.CREATE;
        } else if (!Objects.equals(item.calendarId(), calendarId)) {
            operation = CalendarJobOperation.MOVE;
        } else {
            operation = CalendarJobOperation.UPDATE;
        }
        String eventId = item.hasEvent() ? item.eventId() : CalendarEventIds.of(item.itemId());
        return new CalendarJobOp(item.itemId(), item.tripId(), operation, eventId, calendarId, truncate(item.name()),
                startLabel(item));
    }

    /** 구글에서 지울 일정. 항목 행이 이미 지워졌을 수 있어서 일정 id로 항목 id를 되찾는다 */
    public static CalendarJobOp delete(CalendarEventRef ref, Long tripId) {
        return new CalendarJobOp(CalendarEventIds.planItemIdOf(ref.eventId()), tripId, CalendarJobOperation.DELETE,
                ref.eventId(), ref.calendarId(), null, null);
    }

    /** 이름·시각을 남겨 두는 삭제 (해제·탈퇴처럼 항목 정보가 아직 있을 때) */
    public static CalendarJobOp delete(CalendarItem item) {
        return new CalendarJobOp(item.itemId(), item.tripId(), CalendarJobOperation.DELETE, item.eventId(),
                item.calendarId(), truncate(item.name()), startLabel(item));
    }

    public boolean writesPlanItem() {
        return operation != CalendarJobOperation.DELETE && planItemId != null;
    }

    private static String startLabel(CalendarItem item) {
        String date = item.date().format(DATE);
        return item.allDay() ? date + " 종일" : date + " " + item.startTime().format(TIME);
    }

    private static String truncate(String name) {
        return name == null || name.length() <= NAME_MAX_LENGTH ? name : name.substring(0, NAME_MAX_LENGTH);
    }
}
