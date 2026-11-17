package com.jkymhs.backend.domain.calendar.entity;

/**
 * 구글 캘린더 연동 상태.
 * ACTIVE: 리프레시 토큰으로 일정을 쓸 수 있다. REVOKED: 구글이 토큰을 거부했다(invalid_grant — 사용자가 구글 계정에서 권한을 끊었거나 토큰이 만료됨).
 * REVOKED면 다시 연동(POST /api/calendar)할 때까지 업로드·자동 동기화를 하지 않는다.
 */
public enum CalendarLinkStatus {
    ACTIVE,
    REVOKED
}
