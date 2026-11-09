package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.CalendarSyncStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 캘린더 도메인에 넘기는 일정 항목 하나. 구글 캘린더 일정을 만들 때 필요한 값(이름·주소·메모·날짜·시간)과 지금 동기화 상태를 담는다.
 * 엔티티를 그대로 넘기지 않는 이유: 캘린더 도메인이 일정 항목을 직접 고치지 못하게 하고, 바꿀 때는
 * {@link TripCalendarSupport}의 의도 메서드(markSynced 등)만 쓰게 하려는 것이다.
 *
 * @param date       dayNumber를 여행 시작일 기준으로 바꾼 실제 날짜
 * @param calendarId 지금 이 항목의 일정이 있는 캘린더 (올린 적 없으면 null)
 * @param eventId    구글 캘린더 일정 id (올린 적 없으면 null)
 * @param companion  여행의 동행 FE 코드 (캘린더 행동 신호에 넣는다)
 */
public record CalendarItem(
        Long itemId,
        Long tripId,
        Long planId,
        Long contentId,
        String name,
        String address,
        String memo,
        int dayNumber,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Integer stayMinutes,
        String calendarId,
        String eventId,
        CalendarSyncStatus syncStatus,
        CalendarState state,
        String companion
) {

    public boolean hasEvent() {
        return eventId != null;
    }

    /** 시작 시각이 없으면 종일 일정으로 올린다 */
    public boolean allDay() {
        return startTime == null;
    }
}
