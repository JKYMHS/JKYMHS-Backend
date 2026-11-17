package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryContributor;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 탈퇴 안내 화면의 "구글 캘린더에 올린 일정 n개가 지워집니다".
 */
@Component
@RequiredArgsConstructor
public class CalendarWithdrawSummaryContributor implements WithdrawSummaryContributor {

    private final TripCalendarSupport tripCalendarSupport;

    @Override
    public void contribute(Long userId, Map<String, Long> counts) {
        counts.put(WithdrawSummaryService.CALENDAR_EVENT_COUNT, (long) tripCalendarSupport.uploadedItems(userId).size());
    }
}
