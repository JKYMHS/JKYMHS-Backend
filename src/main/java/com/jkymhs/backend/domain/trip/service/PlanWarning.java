package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 일정 항목 경고. 저장하지 않고 상세를 읽을 때마다 지금 콘텐츠 정보(운영 시간·쉬는 날·축제 기간)로 계산한다.
 * 운영 정보는 매일 동기화로 바뀔 수 있어서, 저장해 두면 금방 낡는다.
 */
public enum PlanWarning implements CodeEnum {
    LUNCH_OVERLAP,      // 11:30~13:30에 걸침 (식당이 아닌 장소)
    DINNER_OVERLAP,     // 17:30~19:30에 걸침 (식당이 아닌 장소)
    AFTER_LAST_ENTRY,   // 도착 시각이 입장 마감 이후
    AFTER_CLOSE,        // 끝나는 시각이 문 닫는 시각 이후
    REST_DAY,           // 그날이 쉬는 날
    OUT_OF_FESTIVAL;    // 그날이 축제 기간 밖

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);
    private static final LocalTime DINNER_START = LocalTime.of(17, 30);
    private static final LocalTime DINNER_END = LocalTime.of(19, 30);

    @Override
    public String code() {
        return name();
    }

    /**
     * @param content 장소 정보. 없거나 삭제됐으면 경고를 계산하지 않는다 (null)
     */
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
        // 자정을 넘겨 닫는 곳(닫는 시각 < 여는 시각)은 비교하지 않는다
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
