package com.jkymhs.backend.domain.trip.entity;

/**
 * 일정 항목 하나의 구글 캘린더 동기화 상태 (plan_items.sync_status). API에는 그대로 내보내지 않고
 * {@code CalendarState}(new/changed/synced/failed)로 바꿔서 내보낸다.
 *
 * <ul>
 *   <li>NONE: 캘린더에 올린 적 없음</li>
 *   <li>DIRTY: 올린 뒤 장소·날짜·시간·메모가 바뀌어서 캘린더 일정이 낡음 (아직 동기화 작업이 잡히지 않음)</li>
 *   <li>PENDING: 동기화 작업이 잡혀서 기다리는 중</li>
 *   <li>SYNCED: 캘린더와 같음</li>
 *   <li>FAILED: 마지막 동기화가 실패함</li>
 * </ul>
 */
public enum CalendarSyncStatus {
    NONE,
    DIRTY,
    PENDING,
    SYNCED,
    FAILED
}
