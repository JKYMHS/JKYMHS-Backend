package com.jkymhs.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증 외 도메인(콘텐츠·탐색·회원·찜·여행·AI 추천·캘린더)의 에러는 브랜치끼리 이 파일을 동시에 고치다 충돌하지 않도록
    // 공통 기반 PR에서 한 번에 정의한다. message는 노션 API 명세서 문구이고, 상황별 문구는
    // CustomException(ErrorCode, message, description)으로 바꿔서 던진다 (code는 그대로).

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록을 처음부터 다시 불러옵니다."),
    SCHEDULE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "일정에는 장소를 최대 30개까지 넣을 수 있습니다."),
    REQUIRED_CONSENT_CANNOT_REVOKE(HttpStatus.BAD_REQUEST, "필수 약관 동의는 철회할 수 없습니다. 회원 탈퇴를 이용해주세요."),
    PLAN_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "확정된 일정안이 없습니다. 일정안을 먼저 확정해주세요."),
    CALENDAR_PERMISSION_DENIED(HttpStatus.BAD_REQUEST, "캘린더 권한을 허용해야 연동할 수 있습니다."),
    CALENDAR_NOT_WRITABLE(HttpStatus.BAD_REQUEST, "선택한 캘린더에 일정을 쓸 수 없습니다."),

    // 401
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),
    IDENTITY_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "본인 확인에 실패했습니다."),
    CALENDAR_AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "구글 캘린더 연결이 만료되었습니다. 다시 연동해주세요."),

    // 403
    CALENDAR_NOT_LINKED(HttpStatus.FORBIDDEN, "구글 캘린더를 먼저 연동해주세요."),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    GOOGLE_NOT_LINKED(HttpStatus.NOT_FOUND, "연결된 구글 계정이 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),

    // 409
    PASSWORD_ALREADY_SET(HttpStatus.CONFLICT, "이미 비밀번호가 설정된 계정입니다."),
    PASSWORD_REQUIRED_FOR_UNLINK(HttpStatus.CONFLICT, "비밀번호를 먼저 설정해야 구글 연결을 해제할 수 있습니다."),
    ONBOARDING_ALREADY_DONE(HttpStatus.CONFLICT, "이미 취향 설정을 완료했습니다."),
    CURSOR_VERSION_MISMATCH(HttpStatus.CONFLICT, "목록이 갱신되었습니다. 처음부터 다시 불러옵니다."),
    SCHEDULE_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 기기에서 일정이 수정되었습니다. 최신 내용을 불러옵니다."),
    SCHEDULE_ITEM_DUPLICATE(HttpStatus.CONFLICT, "이미 같은 날에 추가된 장소입니다."),
    LAST_PLAN_CANNOT_DELETE(HttpStatus.CONFLICT, "마지막 일정안은 삭제할 수 없습니다."),
    VISIT_CHECK_NOT_AVAILABLE(HttpStatus.CONFLICT, "아직 끝나지 않았거나 이미 확인한 여행입니다."),
    CALENDAR_SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "이미 동기화가 진행 중입니다."),

    // 422 (Spring 7에서 UNPROCESSABLE_ENTITY는 deprecated, 같은 422인 UNPROCESSABLE_CONTENT를 쓴다)
    PLAN_EXCEEDS_TRIP_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT, "일정안 기간이 여행 기간보다 깁니다."),
    SCHEDULE_ALREADY_ENDED(HttpStatus.UNPROCESSABLE_CONTENT, "지난 일정에는 장소를 추가할 수 없습니다."),

    // 429
    AI_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 추천 횟수(10회)를 모두 사용했습니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요."),

    // 502
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스와 통신하지 못했습니다. 잠시 후 다시 시도해주세요."),

    // 503
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해주세요."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "추천 서비스를 잠시 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
