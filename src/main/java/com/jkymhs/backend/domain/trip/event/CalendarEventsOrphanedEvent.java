package com.jkymhs.backend.domain.trip.event;

import java.util.List;

/**
 * 캘린더에 올라간 일정 항목이 지워진다 (여행·일정안 삭제, 전체 저장에서 빠진 항목, 여행 기간 축소, 탈퇴).
 * 행을 지우기 전에, 같은 트랜잭션 안에서 발행한다. 지운 뒤에는 calendar_event_id를 읽을 수 없기 때문이다.
 * 캘린더 도메인의 동기 리스너가 "구글 캘린더에서 이 일정들을 지워라" 작업을 넣는다.
 */
public record CalendarEventsOrphanedEvent(Long userId, Long tripId, List<CalendarEventRef> events) {
}
