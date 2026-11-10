package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.service.WithdrawSummaryService.WithdrawSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 없이 가짜 기여자(람다)를 넣어 합치는 규칙만 확인한다. 실제 기여자는 여행·찜·캘린더 PR이 추가한다.
 */
class WithdrawSummaryServiceTest {

    @Test
    @DisplayName("여러 도메인이 넣은 숫자를 합치고, 아무도 넣지 않은 키는 0이다")
    void summarize() {
        WithdrawSummaryContributor trips = (userId, counts) -> {
            counts.put(WithdrawSummaryService.TRIP_COUNT, 5L);
            counts.put(WithdrawSummaryService.PLAN_COUNT, 9L);
        };
        WithdrawSummaryContributor wishes = (userId, counts) -> counts.put(WithdrawSummaryService.WISH_COUNT, userId);

        WithdrawSummary summary = new WithdrawSummaryService(List.of(trips, wishes)).summarize(3L);

        assertThat(summary).isEqualTo(new WithdrawSummary(5, 9, 3, 0));
    }

    @Test
    @DisplayName("기여자가 하나도 없으면 모두 0이다")
    void noContributors() {
        assertThat(new WithdrawSummaryService(List.of()).summarize(1L)).isEqualTo(new WithdrawSummary(0, 0, 0, 0));
    }
}
