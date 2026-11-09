package com.jkymhs.backend.domain.calendar.service;

/**
 * 일괄 업로드에서 여행을 건너뛴 이유.
 */
public enum BulkSkipReason {
    // 확정 일정안이 없다 (FE가 "확정 전 여행" 안내를 따로 보여준다)
    NOT_CONFIRMED,
    // 확정안의 모든 항목이 이미 이 캘린더와 같다
    ALREADY_SYNCED
}
