package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.client.GoogleOAuthClient;
import com.jkymhs.backend.domain.auth.client.GoogleUserInfo;
import com.jkymhs.backend.domain.auth.dto.request.GoogleLinkRequest;
import com.jkymhs.backend.domain.auth.dto.request.GoogleLoginRequest;
import com.jkymhs.backend.domain.auth.dto.response.GoogleLinkResponse;
import com.jkymhs.backend.domain.auth.dto.response.GoogleLoginResponse;
import com.jkymhs.backend.domain.auth.service.GoogleLinkTokenService.LinkData;
import com.jkymhs.backend.domain.auth.service.LoginAttemptService.LoginAttempt;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleLinkTokenService googleLinkTokenService;
    private final UserService userService;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    public AuthResult<GoogleLoginResponse> login(GoogleLoginRequest request, String userAgent) {
        GoogleUserInfo google = googleOAuthClient.fetchUserInfo(request.code(), request.redirectUri());

        Optional<AuthResult<GoogleLoginResponse>> existing = loginExistingUser(google, userAgent);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user;
        try {
            user = userService.register(User.createWithGoogle(google.email(), google.sub(), google.email()));
        } catch (DataIntegrityViolationException e) {
            return loginExistingUser(google, userAgent).orElseThrow(() -> e);
        }
        IssuedTokens tokens = tokenService.issue(user, userAgent);
        return new AuthResult<>(GoogleLoginResponse.signedUp(tokens, user.getOnboardingStatus()), tokens.refreshToken());
    }

    private Optional<AuthResult<GoogleLoginResponse>> loginExistingUser(GoogleUserInfo google, String userAgent) {
        Optional<User> linkedUser = userService.findByGoogleSub(google.sub());
        if (linkedUser.isPresent()) {
            User user = linkedUser.get();
            IssuedTokens tokens = tokenService.issue(user, userAgent);
            return Optional.of(new AuthResult<>(GoogleLoginResponse.loggedIn(tokens, user.getOnboardingStatus()), tokens.refreshToken()));
        }

        Optional<User> sameEmailUser = userService.findByEmail(google.email());
        if (sameEmailUser.isPresent()) {
            return Optional.of(linkRequired(sameEmailUser.get(), google));
        }
        return Optional.empty();
    }

    private AuthResult<GoogleLoginResponse> linkRequired(User user, GoogleUserInfo google) {
        if (!user.hasPassword() || user.isGoogleLinked()) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS, "같은 이메일 계정에 다른 구글 계정이 연결됨");
        }
        String linkToken = googleLinkTokenService.issue(user.getId(), google.sub(), google.email());
        return new AuthResult<>(GoogleLoginResponse.linkRequired(linkToken, user.getEmail()), null);
    }

    @Transactional
    public AuthResult<GoogleLinkResponse> link(GoogleLinkRequest request, String userAgent) {
        LinkData link = googleLinkTokenService.find(request.linkToken())
                .orElseThrow(GoogleAuthService::expiredLinkToken);
        User user = getLinkTarget(link.memberId());
        String email = user.getEmail();

        LoginAttempt attempt = loginAttemptService.start(email);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(attempt);
            throw new CustomException(ErrorCode.INVALID_LINK_PASSWORD);
        }
        loginAttemptService.reset(email);

        if (user.isGoogleLinked() || userService.findByGoogleSub(link.googleSub()).isPresent()) {
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }
        try {
            userService.linkGoogle(user, link.googleSub(), link.googleEmail());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }

        googleLinkTokenService.delete(request.linkToken());
        IssuedTokens tokens = tokenService.issue(user, userAgent);
        return new AuthResult<>(GoogleLinkResponse.of(tokens, user.getOnboardingStatus()), tokens.refreshToken());
    }

    private User getLinkTarget(Long memberId) {
        try {
            return userService.getById(memberId);
        } catch (CustomException e) {
            throw expiredLinkToken();
        }
    }

    private static CustomException expiredLinkToken() {
        return new CustomException(ErrorCode.INVALID_LINK_PASSWORD, "linkToken 만료");
    }
}
