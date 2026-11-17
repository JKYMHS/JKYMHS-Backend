package com.jkymhs.backend.domain.calendar.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 캘린더 작업 종류. 사용자가 누른 작업(UPLOAD·BULK_UPLOAD)은 회원당 하나만 돌 수 있고(409),
 * 서버가 스스로 만드는 작업(SYNC·MOVE·DELETE_*)은 막지도 막히지도 않는다.
 */
public enum CalendarJobType {
    // 여행 하나 업로드 (POST /api/trips/{id}/calendar-uploads)
    UPLOAD,
    // 여러 여행 한꺼번에 (POST /api/calendar/jobs)
    BULK_UPLOAD,
    // 확정안이 바뀌어서 자동으로 고치기
    SYNC,
    // 다른 캘린더로 옮기기
    MOVE,
    // 여행·일정안·항목이 지워져서 그 일정들 지우기
    DELETE_EVENTS,
    // 연동 해제("모두 지우기")·탈퇴: 이 회원이 올린 일정 전부 지우기
    DELETE_ALL;

    public static final Set<CalendarJobType> USER_INITIATED = EnumSet.of(UPLOAD, BULK_UPLOAD);

    public boolean userInitiated() {
        return USER_INITIATED.contains(this);
    }
}
