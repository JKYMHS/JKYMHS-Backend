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

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일 인증이 만료되었습니다. 다시 인증해주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요."),
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "구글 인증에 실패했습니다. 다시 시도해주세요."),
    INVALID_LINK_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),

    // 409
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    GOOGLE_ONLY_ACCOUNT(HttpStatus.CONFLICT, "구글로 가입한 계정입니다. 구글 로그인을 이용해주세요."),
    GOOGLE_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "다른 계정에 이미 연결된 구글 계정입니다."),

    // 410
    VERIFICATION_CODE_EXPIRED(HttpStatus.GONE, "인증 코드가 만료되었습니다. 다시 요청해주세요."),

    // 423
    LOGIN_LOCKED(HttpStatus.LOCKED, "로그인 시도가 많아 10분간 잠겼습니다."),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요."),

    // 502
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."),
    GOOGLE_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "구글 서버와 통신하지 못했습니다."),

    // 503
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
