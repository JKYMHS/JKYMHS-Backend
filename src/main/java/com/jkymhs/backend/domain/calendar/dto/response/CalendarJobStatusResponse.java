package com.jkymhs.backend.domain.calendar.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jkymhs.backend.domain.calendar.entity.CalendarFailureReason;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;

import java.util.List;

/**
 * 캘린더 작업 진행 상태 (FE가 pollAfterMs마다 다시 묻는다). 끝난 작업이면 pollAfterMs 필드 자체가 빠진다.
 *
 * @param retryable 끝났고 실패한 항목이 있어서 "실패만 다시 올리기"를 보여줄 수 있다
 */
public record CalendarJobStatusResponse(
        Long jobId,
        CalendarJobType type,
        CalendarJobStatus status,
        int total,
        int succeeded,
        int failed,
        List<FailedItem> failedItems,
        List<Trip> trips,
        boolean retryable,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer pollAfterMs
) {

    /**
     * @param itemId    일정 항목 id (지워진 항목이면 null일 수 있다)
     * @param startLabel "11.10 09:00" / "11.10 종일"
     */
    public record FailedItem(Long itemId, Long tripId, String name, String startLabel,
                             CalendarFailureReason reasonCode, String reason) {
    }

    /**
     * @param title 지워진 여행이면 null
     */
    public record Trip(Long tripId, String title, int succeeded, int total) {
    }
}
