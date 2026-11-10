package com.jkymhs.backend.domain.trip.event;

import java.util.List;

/**
 * 확정 일정안이 다른 일정안으로 바뀌었다. 예전 확정안의 캘린더 일정(oldEvents)은 지우고 새 확정안을 올려야 한다.
 *
 * 트랜잭션 안에서 발행한다. 캘린더 도메인의 동기 리스너가 같은 트랜잭션에서 작업 행을 넣는다
 * (확정 변경이 롤백되면 작업도 같이 사라진다).
 *
 * @param oldPlanId 예전 확정안. 확정안이 없었으면 null
 * @param oldEvents 예전 확정안에서 캘린더에 올라간 일정들
 */
public record ConfirmedPlanSwitchedEvent(Long userId, Long tripId, Long oldPlanId, Long newPlanId,
                                         List<CalendarEventRef> oldEvents) {
}
