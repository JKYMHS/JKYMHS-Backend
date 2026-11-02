package com.jkymhs.backend.domain.auth.controller;

import com.jkymhs.backend.domain.auth.dto.request.LoginRequest;
import com.jkymhs.backend.domain.auth.dto.request.LogoutRequest;
import com.jkymhs.backend.domain.auth.dto.request.ReissueRequest;
import com.jkymhs.backend.domain.auth.dto.request.SignupRequest;
import com.jkymhs.backend.domain.auth.dto.response.LoginResponse;
import com.jkymhs.backend.domain.auth.dto.response.ReissueResponse;
import com.jkymhs.backend.domain.auth.service.AuthService;
import com.jkymhs.backend.domain.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-Agent는 리프레시 토큰에 "어느 기기의 로그인인지" 기록하려고 받는다. 없어도 로그인은 된다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signup(
            @Valid @RequestBody SignupRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return authService.signup(request, userAgent);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest httpRequest
    ) {
        // IP 요청 제한에 쓰는 접속 IP. 프록시(로드밸런서) 뒤에 배포하면 프록시 IP가 찍히므로 배포 구성에 맞게 바꿔야 한다
        return authService.login(request, httpRequest.getRemoteAddr(), userAgent);
    }

    @PostMapping("/reissue")
    public ReissueResponse reissue(
            @Valid @RequestBody ReissueRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return ReissueResponse.from(tokenService.reissue(request.refreshToken(), userAgent));
    }

    // SecurityConfig에서 로그아웃만 인증이 필요하게 막아 두어서, 여기까지 오면 userId는 항상 있다
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody LogoutRequest request
    ) {
        tokenService.logout(userId, request.refreshToken());
    }
}
