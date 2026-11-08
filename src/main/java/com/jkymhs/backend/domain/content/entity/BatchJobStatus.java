package com.jkymhs.backend.domain.content.entity;

/**
 * RUNNING: 실행 중(서버가 중간에 죽으면 이 상태로 남는다) · SUCCESS: 다 끝남 ·
 * PARTIAL: 일부만 끝남(호출 한도 초과·일부 항목 실패, 다음 실행이 이어간다) · FAILED: 예외로 중단.
 */
public enum BatchJobStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED
}
