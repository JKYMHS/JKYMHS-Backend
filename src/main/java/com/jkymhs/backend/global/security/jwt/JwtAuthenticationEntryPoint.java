package com.jkymhs.backend.global.security.jwt;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * 인증이 필요한 API에 로그인 없이(또는 잘못된 토큰으로) 접근했을 때 401을 내려준다.
 *
 * 시큐리티 필터는 컨트롤러보다 앞에서 동작해서 @RestControllerAdvice(GlobalExceptionHandler)가 예외를 잡지 못한다.
 * 그래서 HandlerExceptionResolver에 CustomException을 직접 넘겨서 GlobalExceptionHandler가 응답을 만들게 한다.
 * 이렇게 하면 401도 다른 에러와 똑같은 {message, status, timestamp, description} 형식이 된다.
 *
 * message는 명세대로 항상 "인증이 필요합니다."이고, 왜 실패했는지는 description으로 구분한다.
 * FE는 인증이 필요한 API에서 이 401을 받으면 토큰 재발급(/api/auth/reissue)을 한 번 시도하면 된다.
 * (로그인 실패, 재발급 실패 같은 /api/auth/** 의 401은 메시지가 다르고, 재발급으로 해결되지 않는다)
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationEntryPoint(HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String description = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE) instanceof String reason
                ? reason
                : "액세스 토큰 없음";
        ModelAndView handled = handlerExceptionResolver.resolveException(request, response, null,
                new CustomException(ErrorCode.UNAUTHORIZED, description));
        // 처리할 핸들러를 못 찾으면 null이 온다. 아무것도 안 하면 인증이 필요한 API가 200 빈 응답으로 나가므로 401만이라도 준다
        if (handled == null && !response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
