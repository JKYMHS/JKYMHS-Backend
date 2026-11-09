package com.jkymhs.backend.domain.calendar.entity;

/**
 * 작업 항목이 실패한 이유 (작업 상태 API의 reasonCode). reason은 화면에 그대로 보여줄 문구다.
 */
public enum CalendarFailureReason {
    // 구글이 403을 줬다: 읽기 전용 캘린더이거나 공유 권한이 바뀌었다
    NOT_WRITABLE("선택한 캘린더에 일정을 쓸 수 없습니다."),
    // 리프레시 토큰이 거부됐다(invalid_grant) 또는 연동이 없어졌다
    AUTH_EXPIRED("구글 캘린더 연결이 만료되었습니다. 다시 연동해주세요."),
    // 그 밖의 구글 오류, 또는 429·5xx를 최대 횟수만큼 다시 시도해도 실패했다
    GOOGLE_ERROR("구글 캘린더에 일정을 올리지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final String reason;

    CalendarFailureReason(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
