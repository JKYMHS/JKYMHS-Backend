package com.jkymhs.backend.domain.auth.controller;

import com.jkymhs.backend.domain.auth.dto.request.LoginRequest;
import com.jkymhs.backend.domain.auth.dto.request.SignupRequest;
import com.jkymhs.backend.domain.auth.dto.response.LoginResponse;
import com.jkymhs.backend.domain.auth.dto.response.ReissueResponse;
import com.jkymhs.backend.domain.auth.service.AuthResult;
import com.jkymhs.backend.domain.auth.service.AuthService;
import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.global.security.RefreshTokenCookieProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 액세스 토큰은 JSON 본문으로, 리프레시 토큰은 httpOnly 쿠키(Set-Cookie)로 내려준다.
 * User-Agent는 리프레시 토큰에 "어느 기기의 로그인인지" 기록하려고 받는다. 없어도 로그인은 된다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(
            @Valid @RequestBody SignupRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthResult<LoginResponse> result = authService.signup(request, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(result.body());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest httpRequest
    ) {
        // IP 요청 제한에 쓰는 접속 IP. 프록시(로드밸런서) 뒤에 배포하면 프록시 IP가 찍히므로 배포 구성에 맞게 바꿔야 한다
        AuthResult<LoginResponse> result = authService.login(request, httpRequest.getRemoteAddr(), userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(result.body());
    }

    // 쿠키가 없을 때 스프링의 400 대신 다른 재발급 실패와 같은 401을 주려고 required = false로 받아서 서비스에서 확인한다
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) Cookie refreshTokenCookie,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        IssuedTokens tokens = tokenService.reissue(valueOf(refreshTokenCookie), userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(tokens.refreshToken()).toString())
                .body(ReissueResponse.from(tokens));
    }

    // SecurityConfig에서 로그아웃만 인증이 필요하게 막아 두어서, 여기까지 오면 userId는 항상 있다
    // 쿠키가 없어도(이미 지워졌어도) 결과는 "이 브라우저에 쿠키가 없는 상태"로 같으므로 항상 204와 삭제 쿠키를 준다
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long userId,
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) Cookie refreshTokenCookie
    ) {
        tokenService.logout(userId, valueOf(refreshTokenCookie));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.expire().toString())
                .build();
    }

    // String으로 받으면 스프링이 값을 URL 디코딩하는데, 쿠키에 %ZZ처럼 잘못된 값이 오면 디코딩 예외로 500이 된다.
    // 쿠키 값은 사용자가 마음대로 넣을 수 있고 우리 토큰(base64url)은 디코딩할 게 없어서, Cookie로 받아 원문을 그대로 쓴다
    private static String valueOf(Cookie cookie) {
        return cookie == null ? null : cookie.getValue();
    }
}
