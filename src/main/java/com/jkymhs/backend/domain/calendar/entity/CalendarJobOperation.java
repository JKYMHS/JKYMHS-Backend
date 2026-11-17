package com.jkymhs.backend.domain.calendar.entity;

/**
 * 작업 항목 하나가 구글 캘린더에 할 일.
 * CREATE: 새 일정 (이미 있으면 고친다) · UPDATE: 고치기 (없으면 새로 만든다) · DELETE: 지우기 (이미 없으면 성공) ·
 * MOVE: 다른 캘린더로 옮긴 뒤 내용도 맞춘다.
 */
public enum CalendarJobOperation {
    CREATE,
    UPDATE,
    DELETE,
    MOVE
}
