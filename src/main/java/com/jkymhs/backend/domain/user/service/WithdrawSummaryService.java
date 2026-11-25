package com.jkymhs.backend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawSummaryService {

    public static final String TRIP_COUNT = "tripCount";
    public static final String PLAN_COUNT = "planCount";
    public static final String WISH_COUNT = "wishCount";
    public static final String CALENDAR_EVENT_COUNT = "calendarEventCount";

    private final List<WithdrawSummaryContributor> contributors;

    public WithdrawSummary summarize(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        contributors.forEach(contributor -> contributor.contribute(userId, counts));
        return new WithdrawSummary(
                counts.getOrDefault(TRIP_COUNT, 0L),
                counts.getOrDefault(PLAN_COUNT, 0L),
                counts.getOrDefault(WISH_COUNT, 0L),
                counts.getOrDefault(CALENDAR_EVENT_COUNT, 0L)
        );
    }

    public record WithdrawSummary(long tripCount, long planCount, long wishCount, long calendarEventCount) {
    }
}
