package com.jkymhs.backend.domain.calendar.entity;

public enum CalendarFailureReason {
    NOT_WRITABLE("선택한 캘린더에 일정을 쓸 수 없습니다."),
    AUTH_EXPIRED("구글 캘린더 연결이 만료되었습니다. 다시 연동해주세요."),
    GOOGLE_ERROR("구글 캘린더에 일정을 올리지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final String reason;

    CalendarFailureReason(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
