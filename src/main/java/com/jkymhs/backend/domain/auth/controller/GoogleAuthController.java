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

    @PostMapping
    public ResponseEntity<GoogleLoginResponse> login(
            @Valid @RequestBody GoogleLoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthResult<GoogleLoginResponse> result = googleAuthService.login(request, userAgent);
        if (result.refreshToken() == null) {
            return ResponseEntity.ok(result.body());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(result.body());
    }

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
