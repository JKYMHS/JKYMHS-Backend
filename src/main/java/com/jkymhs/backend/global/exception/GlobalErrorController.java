package com.jkymhs.backend.global.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러까지 오지 못한 에러도 공통 에러 형식으로 내려준다.
 *
 * {@link GlobalExceptionHandler}는 컨트롤러 단계의 예외만 잡는다. 스프링 시큐리티 방화벽이 이상한 URL(/api//x, /api/x;y=1)을
 * 거절하거나 필터에서 sendError를 부르면 예외 처리 없이 톰캣이 요청을 /error로 다시 보낸다.
 * 이 빈이 있으면 스프링 부트 기본 응답({timestamp, status, error, path})을 만드는 BasicErrorController는 등록되지 않는다.
 */
@RestController
public class GlobalErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        HttpStatusCode status = resolveStatus(request);
        ErrorCode errorCode = toErrorCode(status);
        // 톰캣이 넘겨주는 에러 메시지와 예외에는 내부 정보가 들어 있을 수 있어서 응답에 담지 않는다
        ErrorResponse body = ErrorResponse.of(errorCode, status, errorCode.getMessage(), errorCode.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // 상태코드가 없으면 에러가 넘어온 게 아니라 누가 /error를 직접 호출한 것이다. 없는 주소처럼 404로 답해서 500 경보가 울리지 않게 한다
    private static HttpStatusCode resolveStatus(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status
                ? HttpStatusCode.valueOf(status)
                : ErrorCode.RESOURCE_NOT_FOUND.getStatus();
    }

    // ErrorCode가 따로 없는 4xx는 GlobalExceptionHandler와 똑같이 INVALID_INPUT이다. FE는 code로만 분기하므로 둘이 달라지면 안 된다
    private static ErrorCode toErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCode.INVALID_INPUT : ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}
