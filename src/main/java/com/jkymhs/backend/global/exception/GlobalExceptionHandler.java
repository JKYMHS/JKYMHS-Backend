package com.jkymhs.backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        return toResponse(ErrorResponse.of(e.getErrorCode(), e.getDescription()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();

        String message = fieldErrors.isEmpty()
                ? errorCode.getMessage()
                : fieldErrors.get(0).getDefaultMessage();
        String description = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return toResponse(ErrorResponse.of(errorCode, message, description));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return toResponse(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 API 경로입니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ErrorResponse body = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, e.getMethod() + " 메서드는 지원하지 않습니다.");
        return toResponse(body, e.getHeaders());
    }

    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleRedisFailure(Exception e) {
        log.error("Redis unavailable: {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE, "일시적인 서버 장애"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return handleSpringClientError(springError);
        }
        log.error("Unhandled exception", e);
        return toResponse(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, "서버 내부 오류"));
    }

    // 위에서 따로 잡지 않은 스프링 MVC 클라이언트 오류(415, 406 등)는 원래 상태코드와 헤더를 유지한다
    private ResponseEntity<ErrorResponse> handleSpringClientError(org.springframework.web.ErrorResponse springError) {
        String detail = springError.getBody().getDetail();
        ErrorResponse body = ErrorResponse.of(
                springError.getStatusCode(),
                ErrorCode.INVALID_INPUT.getMessage(),
                detail != null ? detail : "요청 형식이 올바르지 않습니다."
        );
        return toResponse(body, springError.getHeaders());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorResponse body) {
        return toResponse(body, HttpHeaders.EMPTY);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorResponse body, HttpHeaders headers) {
        return ResponseEntity.status(body.status()).headers(headers).body(body);
    }
}
