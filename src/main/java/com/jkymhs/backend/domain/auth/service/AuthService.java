package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.dto.request.LoginRequest;
import com.jkymhs.backend.domain.auth.dto.request.SignupRequest;
import com.jkymhs.backend.domain.auth.dto.response.LoginResponse;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.service.LoginAttemptService.LoginAttempt;
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

/**
 * 이메일 회원가입과 이메일·비밀번호 로그인.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /*
     * 없는 이메일로 로그인할 때 비교에 쓰는 가짜 BCrypt 해시 (아무 비밀번호와도 맞지 않는다).
     * BCrypt 비교는 일부러 느리게(수십 ms) 만든 연산이라, 없는 이메일만 비교 없이 바로 401을 주면
     * 응답 시간 차이로 가입 여부를 알아낼 수 있다. 그래서 없는 이메일도 같은 비용으로 한 번 비교한다.
     * PasswordEncoder와 같은 cost($10$)여야 시간이 비슷하다.
     */
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$CNWiVCyMrhvd/ShAXGmPwepYsTiGsWpi69.OsvlOT9.lgVF.bagBi";

    private final UserService userService;
    private final UserConsentService userConsentService;
    private final VerificationTokenService verificationTokenService;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원·약관 동의·리프레시 토큰 저장을 한 트랜잭션으로 묶는다. 중간에 실패하면 약관 동의 없는 회원 같은 반쪽 데이터가 남지 않는다.
     * (verificationToken은 Redis라서 롤백되지 않는다. 확인하는 순간 폐기된다)
     */
    @Transactional
    public LoginResponse signup(SignupRequest request, String userAgent) {
        verificationTokenService.consume(VerificationPurpose.SIGNUP, request.email(), request.verificationToken());

        if (userService.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = register(request.email(), request.password());
        userConsentService.saveInitialConsents(user, request.consents().agreedTypes());

        return LoginResponse.of(tokenService.issue(user, userAgent), user.getOnboardingStatus());
    }

    /**
     * 순서: IP 제한(429) → 구글 전용 계정 확인(409) → 계정 잠금 확인과 시도 기록(423) → 비밀번호 확인.
     * 잠금 확인을 비밀번호 확인보다 먼저 해야 잠긴 동안에는 비밀번호를 맞혀도 로그인되지 않는다.
     */
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        rateLimitService.check(RateLimitType.LOGIN, ip);
        String email = request.email();

        User user = userService.findByEmail(email).orElse(null);
        // 구글 전용 계정은 비밀번호가 틀린 게 아니라 로그인 방법이 다른 것이라 실패 횟수에 넣지 않는다.
        // 그래서 시도를 기록하는 start()보다 먼저 확인한다
        if (user != null && !user.hasPassword()) {
            throw new CustomException(ErrorCode.GOOGLE_ONLY_ACCOUNT, "비밀번호가 없는 구글 전용 계정");
        }
        // 잠겨 있으면 423. 아니면 비밀번호를 비교하기 전에 이번 시도를 먼저 기록한다 (동시에 여러 번 보내도 5번까지만 비교)
        LoginAttempt attempt = loginAttemptService.start(email);
        if (!passwordMatches(user, request.password())) {
            // 이번이 5번째 실패면 여기서 423이 나간다
            loginAttemptService.recordFailure(attempt);
            // 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 401을 줘서 가입 여부를 드러내지 않는다
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptService.reset(email);
        return LoginResponse.of(tokenService.issue(user, userAgent), user.getOnboardingStatus());
    }

    // 위의 existsByEmail 확인과 저장 사이에 같은 이메일 가입이 동시에 들어오면 DB UNIQUE 제약이 막아준다. 그것도 409로 응답한다
    private User register(String email, String rawPassword) {
        try {
            return userService.register(User.createWithPassword(email, passwordEncoder.encode(rawPassword)));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
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
