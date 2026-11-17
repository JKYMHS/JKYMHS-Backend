package com.jkymhs.backend.domain.auth.controller;

import com.jkymhs.backend.domain.auth.dto.request.GoogleLinkRequest;
import com.jkymhs.backend.domain.auth.dto.request.GoogleLoginRequest;
import com.jkymhs.backend.domain.auth.dto.response.GoogleLinkResponse;
import com.jkymhs.backend.domain.auth.dto.response.GoogleLoginResponse;
import com.jkymhs.backend.domain.auth.service.AuthResult;
import com.jkymhs.backend.domain.auth.service.GoogleAuthService;
import com.jkymhs.backend.global.security.RefreshTokenCookieProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    // FE가 구글 동의 화면에서 받은 인가 코드를 넘긴다. 구글과의 토큰 교환은 서버가 한다 (client secret은 서버에만 있다)
    @PostMapping
    public ResponseEntity<GoogleLoginResponse> login(
            @Valid @RequestBody GoogleLoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthResult<GoogleLoginResponse> result = googleAuthService.login(request, userAgent);
        // LINK_REQUIRED는 아직 로그인이 아니라서 내려줄 토큰도 쿠키도 없다
        if (result.refreshToken() == null) {
            return ResponseEntity.ok(result.body());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(result.body());
    }

    // 구글 로그인 결과가 LINK_REQUIRED일 때, 기존 계정 비밀번호를 확인하고 구글 계정을 연결한 뒤 로그인한다
    @PostMapping("/link")
    public ResponseEntity<GoogleLinkResponse> link(
            @Valid @RequestBody GoogleLinkRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthResult<GoogleLinkResponse> result = googleAuthService.link(request, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(result.body());
    }
}
