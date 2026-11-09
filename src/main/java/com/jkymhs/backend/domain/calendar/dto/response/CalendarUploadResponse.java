package com.jkymhs.backend.domain.calendar.dto.response;

/**
 * @param itemCount   이 작업이 구글에 할 일의 수 (캘린더를 바꿔서 함께 옮기는 일정 포함)
 * @param pollAfterMs FE가 작업 상태를 다시 물어볼 간격
 */
public record CalendarUploadResponse(Long jobId, int itemCount, int pollAfterMs) {
}
