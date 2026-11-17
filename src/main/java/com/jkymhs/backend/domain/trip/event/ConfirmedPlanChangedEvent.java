package com.jkymhs.backend.domain.trip.event;

/**
 * 확정 일정안의 내용(장소·날짜·시간·메모)이 바뀌었다. 캘린더 도메인이 받아서 자동 동기화 작업을 잡는다.
 *
 * 저장 트랜잭션 안에서 발행한다. 받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받아서
 * 저장이 실제로 커밋된 뒤에만 동기화한다 (저장이 롤백되면 동기화도 하지 않는다).
 */
public record ConfirmedPlanChangedEvent(Long userId, Long tripId, Long planId) {
}
