package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public enum PlanWarning implements CodeEnum {
    LUNCH_OVERLAP,
    DINNER_OVERLAP,
    AFTER_LAST_ENTRY,
    AFTER_CLOSE,
    REST_DAY,
    OUT_OF_FESTIVAL;

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);
    private static final LocalTime DINNER_START = LocalTime.of(17, 30);
    private static final LocalTime DINNER_END = LocalTime.of(19, 30);

    @Override
    public String code() {
        return name();
    }

    public static List<PlanWarning> of(PlanItem item, ContentSnapshot content, LocalDate date) {
        List<PlanWarning> warnings = new ArrayList<>();
        if (content == null || content.deleted()) {
            return warnings;
        }
        LocalTime start = item.getStartTime();
        LocalTime end = item.getEndTime();
        if (end == null && start != null && item.getStayMinutes() != null) {
            end = start.plusMinutes(item.getStayMinutes());
        }
        if (start != null && end != null && content.contentType() != ContentType.RESTAURANT) {
            if (overlaps(start, end, LUNCH_START, LUNCH_END)) {
                warnings.add(LUNCH_OVERLAP);
            }
            if (overlaps(start, end, DINNER_START, DINNER_END)) {
                warnings.add(DINNER_OVERLAP);
            }
        }
        if (start != null && content.lastEntryTime() != null && start.isAfter(content.lastEntryTime())) {
            warnings.add(AFTER_LAST_ENTRY);
        }
        boolean overnight = content.openTime() != null && content.closeTime() != null
                && content.closeTime().isBefore(content.openTime());
        if (end != null && content.closeTime() != null && !overnight && end.isAfter(content.closeTime())) {
            warnings.add(AFTER_CLOSE);
        }
        if (content.restDays() != null && content.restDays().contains(date.getDayOfWeek())) {
            warnings.add(REST_DAY);
        }
        if (isOutOfFestival(content, date)) {
            warnings.add(OUT_OF_FESTIVAL);
        }
        return warnings;
    }

    public static boolean isOutOfFestival(ContentSnapshot content, LocalDate date) {
        return content.contentType() == ContentType.FESTIVAL && content.eventStartDate() != null
                && content.eventEndDate() != null
                && (date.isBefore(content.eventStartDate()) || date.isAfter(content.eventEndDate()));
    }

    private static boolean overlaps(LocalTime start, LocalTime end, LocalTime rangeStart, LocalTime rangeEnd) {
        return start.isBefore(rangeEnd) && end.isAfter(rangeStart);
    }
}
