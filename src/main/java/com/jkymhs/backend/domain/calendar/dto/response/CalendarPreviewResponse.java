package com.jkymhs.backend.domain.calendar.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.trip.service.CalendarState;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 여행 업로드 화면. 확정 일정안의 모든 날을 days로 내려준다 (장소가 없는 날도 빈 items로).
 *
 * @param calendarId 기본으로 고를 캘린더: 이 여행에 마지막으로 쓴 캘린더, 없으면 기본 캘린더
 */
public record CalendarPreviewResponse(
        Long tripId,
        Long planId,
        String planName,
        String calendarId,
        List<Day> days
) {

    public record Day(int dayNumber, LocalDate date, List<Item> items) {
    }

    /**
     * @param durationMinutes 시작~끝 시각 차이, 끝 시각이 없으면 머무는 시간. 둘 다 없으면 null
     * @param allDay          시작 시각이 없어서 종일 일정으로 올라간다
     * @param state           new / changed / synced / failed
     */
    public record Item(
            Long itemId,
            String name,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Integer durationMinutes,
            boolean allDay,
            CalendarState state
    ) {
    }
}
