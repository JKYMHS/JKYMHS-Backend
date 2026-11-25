package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.dto.request.LoginRequest;
import com.jkymhs.backend.domain.auth.dto.request.PasswordResetRequest;
import com.jkymhs.backend.domain.auth.dto.request.SignupRequest;
import com.jkymhs.backend.domain.auth.dto.response.LoginResponse;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.service.LoginAttemptService.LoginAttempt;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$10$CNWiVCyMrhvd/ShAXGmPwepYsTiGsWpi69.OsvlOT9.lgVF.bagBi";

    private final UserService userService;
    private final UserConsentService userConsentService;
    private final VerificationTokenService verificationTokenService;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResult<LoginResponse> signup(SignupRequest request, String userAgent) {
        verificationTokenService.consume(VerificationPurpose.SIGNUP, request.email(), request.verificationToken());

        if (userService.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = register(request.email(), request.password());
        userConsentService.saveInitialConsents(user, request.consents().agreedTypes());

        return issueTokens(user, userAgent);
    }

    public AuthResult<LoginResponse> login(LoginRequest request, String ip, String userAgent) {
        rateLimitService.check(RateLimitType.LOGIN, ip);
        String email = request.email();

        User user = userService.findByEmail(email).orElse(null);
        if (user != null && !user.hasPassword()) {
            throw new CustomException(ErrorCode.GOOGLE_ONLY_ACCOUNT, "비밀번호가 없는 구글 전용 계정");
        }
        LoginAttempt attempt = loginAttemptService.start(email);
        if (!passwordMatches(user, request.password())) {
            loginAttemptService.recordFailure(attempt);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptService.reset(email);
        return issueTokens(user, userAgent);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        String email = request.email();
        verificationTokenService.consume(VerificationPurpose.RESET_PASSWORD, email, request.verificationToken());

        User user = getResetTarget(email);
        user.changePassword(passwordEncoder.encode(request.newPassword()));

        tokenService.revokeAll(user.getId());
        // Redis는 롤백되지 않아서 DB 작업 뒤에 지운다
        loginAttemptService.reset(email);
    }

    private AuthResult<LoginResponse> issueTokens(User user, String userAgent) {
        IssuedTokens tokens = tokenService.issue(user, userAgent);
        return new AuthResult<>(LoginResponse.of(tokens, user.getOnboardingStatus()), tokens.refreshToken());
    }

    private User register(String email, String rawPassword) {
        try {
            return userService.register(User.createWithPassword(email, passwordEncoder.encode(rawPassword)));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private User getResetTarget(String email) {
        Long userId = userService.findByEmail(email)
                .map(User::getId)
                .orElseThrow(VerificationTokenService::notVerified);
        try {
            return userService.getByIdForUpdate(userId);
        } catch (CustomException e) {
            throw VerificationTokenService.notVerified();
        }
    }

    private boolean passwordMatches(User user, String rawPassword) {
        if (user == null) {
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }
}
