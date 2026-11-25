package com.jkymhs.backend.global.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GlobalErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        HttpStatusCode status = resolveStatus(request);
        ErrorCode errorCode = toErrorCode(status);
        ErrorResponse body = ErrorResponse.of(errorCode, status, errorCode.getMessage(), errorCode.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static HttpStatusCode resolveStatus(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status
                ? HttpStatusCode.valueOf(status)
                : ErrorCode.RESOURCE_NOT_FOUND.getStatus();
    }

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
