package com.jkymhs.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 목록. message는 노션 API 명세서 문구를 그대로 쓴다 (API 명세서와 에러 코드 DB가 다르면 API 명세서 기준).
 * 인증 관련 에러는 #3~#6이 같은 파일을 동시에 고치다 충돌하지 않도록 #2에서 한 번에 정의했다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증 외 도메인(콘텐츠·탐색·회원·찜·여행·AI 추천·캘린더)의 에러는 브랜치끼리 이 파일을 동시에 고치다 충돌하지 않도록
    // 공통 기반 PR에서 한 번에 정의한다. message는 노션 API 명세서 문구이고, 상황별 문구는
    // CustomException(ErrorCode, message, description)으로 바꿔서 던진다 (code는 그대로).

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록을 처음부터 다시 불러옵니다."),
    SCHEDULE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "일정에는 장소를 최대 30개까지 넣을 수 있습니다."),
    REQUIRED_CONSENT_CANNOT_REVOKE(HttpStatus.BAD_REQUEST, "필수 약관 동의는 철회할 수 없습니다. 회원 탈퇴를 이용해주세요."),
    PLAN_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "확정된 일정안이 없습니다. 일정안을 먼저 확정해주세요."),
    CALENDAR_PERMISSION_DENIED(HttpStatus.BAD_REQUEST, "캘린더 권한을 허용해야 연동할 수 있습니다."),
    CALENDAR_NOT_WRITABLE(HttpStatus.BAD_REQUEST, "선택한 캘린더에 일정을 쓸 수 없습니다."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일 인증이 만료되었습니다. 다시 인증해주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요."),
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "구글 인증에 실패했습니다. 다시 시도해주세요."),
    INVALID_LINK_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),
    IDENTITY_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "본인 확인에 실패했습니다."),
    CALENDAR_AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "구글 캘린더 연결이 만료되었습니다. 다시 연동해주세요."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CALENDAR_NOT_LINKED(HttpStatus.FORBIDDEN, "구글 캘린더를 먼저 연동해주세요."),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    GOOGLE_NOT_LINKED(HttpStatus.NOT_FOUND, "연결된 구글 계정이 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),

    // 409
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    GOOGLE_ONLY_ACCOUNT(HttpStatus.CONFLICT, "구글로 가입한 계정입니다. 구글 로그인을 이용해주세요."),
    GOOGLE_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "다른 계정에 이미 연결된 구글 계정입니다."),
    PASSWORD_ALREADY_SET(HttpStatus.CONFLICT, "이미 비밀번호가 설정된 계정입니다."),
    PASSWORD_REQUIRED_FOR_UNLINK(HttpStatus.CONFLICT, "비밀번호를 먼저 설정해야 구글 연결을 해제할 수 있습니다."),
    ONBOARDING_ALREADY_DONE(HttpStatus.CONFLICT, "이미 취향 설정을 완료했습니다."),
    CURSOR_VERSION_MISMATCH(HttpStatus.CONFLICT, "목록이 갱신되었습니다. 처음부터 다시 불러옵니다."),
    SCHEDULE_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 기기에서 일정이 수정되었습니다. 최신 내용을 불러옵니다."),
    SCHEDULE_ITEM_DUPLICATE(HttpStatus.CONFLICT, "이미 같은 날에 추가된 장소입니다."),
    LAST_PLAN_CANNOT_DELETE(HttpStatus.CONFLICT, "마지막 일정안은 삭제할 수 없습니다."),
    VISIT_CHECK_NOT_AVAILABLE(HttpStatus.CONFLICT, "아직 끝나지 않았거나 이미 확인한 여행입니다."),
    CALENDAR_SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "이미 동기화가 진행 중입니다."),

    // 410
    VERIFICATION_CODE_EXPIRED(HttpStatus.GONE, "인증 코드가 만료되었습니다. 다시 요청해주세요."),

    // 422 (Spring 7에서 UNPROCESSABLE_ENTITY는 deprecated, 같은 422인 UNPROCESSABLE_CONTENT를 쓴다)
    PLAN_EXCEEDS_TRIP_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT, "일정안 기간이 여행 기간보다 깁니다."),
    SCHEDULE_ALREADY_ENDED(HttpStatus.UNPROCESSABLE_CONTENT, "지난 일정에는 장소를 추가할 수 없습니다."),

    // 423
    LOGIN_LOCKED(HttpStatus.LOCKED, "로그인 시도가 많아 10분간 잠겼습니다."),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),
    CODE_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),
    AI_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 추천 횟수(10회)를 모두 사용했습니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요."),

    // 502
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."),
    GOOGLE_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "구글 서버와 통신하지 못했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스와 통신하지 못했습니다. 잠시 후 다시 시도해주세요."),

    // 503
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해주세요."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "추천 서비스를 잠시 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
