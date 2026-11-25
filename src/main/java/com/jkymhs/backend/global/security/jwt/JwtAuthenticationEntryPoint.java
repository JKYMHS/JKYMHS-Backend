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
        if (handled == null && !response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
