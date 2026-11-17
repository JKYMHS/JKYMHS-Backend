package com.jkymhs.backend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 탈퇴하면 지워지는 데이터 개수. 등록된 {@link WithdrawSummaryContributor}를 모두 불러서 합친다.
 * 아직 그 도메인이 없으면(여행·찜·캘린더 PR 전) 구현 빈이 없어서 해당 숫자는 0이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawSummaryService {

    public static final String TRIP_COUNT = "tripCount";
    public static final String PLAN_COUNT = "planCount";
    public static final String WISH_COUNT = "wishCount";
    public static final String CALENDAR_EVENT_COUNT = "calendarEventCount";

    // 구현 빈이 하나도 없으면 스프링이 빈 리스트를 넣는다
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
