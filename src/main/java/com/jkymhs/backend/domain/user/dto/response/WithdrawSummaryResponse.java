package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.service.WithdrawSummaryService.WithdrawSummary;

public record WithdrawSummaryResponse(long tripCount, long planCount, long wishCount, long calendarEventCount) {

    public static WithdrawSummaryResponse from(WithdrawSummary summary) {
        return new WithdrawSummaryResponse(summary.tripCount(), summary.planCount(), summary.wishCount(),
                summary.calendarEventCount());
    }
}
