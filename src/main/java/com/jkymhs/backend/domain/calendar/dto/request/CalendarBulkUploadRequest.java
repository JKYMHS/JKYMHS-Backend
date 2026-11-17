package com.jkymhs.backend.domain.calendar.dto.request;

import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 여러 여행 한꺼번에 올리기. 10개 제한은 여기서 @Size로 막지 않는다 — 에러 code가 INVALID_INPUT이 아니라
 * SCHEDULE_LIMIT_EXCEEDED여야 해서 서비스에서 확인한다.
 *
 * @param type 지금은 BULK_UPLOAD만 받는다 (같은 주소로 다른 작업 종류를 늘릴 수 있게 FE가 정한 모양)
 */
public record CalendarBulkUploadRequest(
        @NotNull(message = "작업 종류를 입력해주세요.") CalendarJobType type,
        @NotEmpty(message = "올릴 여행을 하나 이상 골라주세요.") List<@NotNull Long> tripIds,
        String calendarId
) {
}
