package com.jkymhs.backend.domain.calendar.dto.response;

/**
 * @param jobId "모두 지우기"를 고르면 삭제 작업 id (진행은 작업 상태 API로 본다). "그대로 두기"면 null
 */
public record CalendarDisconnectResponse(Long jobId) {
}
