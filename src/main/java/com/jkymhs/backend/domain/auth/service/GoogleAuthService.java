package com.jkymhs.backend.domain.auth.service;

import com.jkymhs.backend.domain.auth.client.GoogleOAuthClient;
import com.jkymhs.backend.domain.auth.client.GoogleUserInfo;
import com.jkymhs.backend.domain.auth.dto.request.GoogleLinkRequest;
import com.jkymhs.backend.domain.auth.dto.request.GoogleLoginRequest;
import com.jkymhs.backend.domain.auth.dto.response.GoogleLinkResponse;
import com.jkymhs.backend.domain.auth.dto.response.GoogleLoginResponse;
import com.jkymhs.backend.domain.auth.service.GoogleLinkTokenService.LinkData;
import com.jkymhs.backend.domain.auth.service.LoginAttemptService.LoginAttempt;
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

/**
 * 구글 로그인(가입 포함)과 로그인 중 구글 계정 연결.
 * 구글 로그인에는 로그인 시도 제한(IP·계정)을 적용하지 않는다 (기능 명세 FUNC-001-02).
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleLinkTokenService googleLinkTokenService;
    private final UserService userService;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 일부러 메서드 전체를 {@code @Transactional}로 묶지 않았다.
     * 묶으면 구글 응답을 기다리는 몇 초 동안 DB 커넥션을 붙잡고 있어서, 구글이 느려지면 커넥션 풀이 바닥나 다른 API까지 멈춘다.
     * 그래서 구글 호출을 먼저 끝내고, DB 작업은 UserService·TokenService가 각자 트랜잭션으로 처리한다.
     * (가입 직후 토큰 발급만 실패하면 계정은 남지만, 다시 구글 로그인하면 LOGGED_IN으로 들어온다)
     */
    public GoogleLoginResponse login(GoogleLoginRequest request, String userAgent) {
        GoogleUserInfo google = googleOAuthClient.fetchUserInfo(request.code(), request.redirectUri());

        // 1) 이 구글 계정(sub)이 연결된 회원 → 로그인
        Optional<User> linkedUser = userService.findByGoogleSub(google.sub());
        if (linkedUser.isPresent()) {
            User user = linkedUser.get();
            return GoogleLoginResponse.loggedIn(tokenService.issue(user, userAgent), user.getOnboardingStatus());
        }

        // 2) 같은 이메일로 가입한 회원 → 비밀번호를 확인한 뒤 연결해야 한다
        Optional<User> sameEmailUser = userService.findByEmail(google.email());
        if (sameEmailUser.isPresent()) {
            return linkRequired(sameEmailUser.get(), google);
        }

        // 3) 처음 보는 구글 계정 → 비밀번호 없는 계정으로 가입
        User user = userService.register(User.createWithGoogle(google.email(), google.sub(), google.email()));
        return GoogleLoginResponse.signedUp(tokenService.issue(user, userAgent), user.getOnboardingStatus());
    }

    /**
     * 같은 이메일이라는 것만으로 자동 연결하지 않고 기존 비밀번호를 한 번 더 확인한다.
     * 비밀번호 확인 없이 연결하면, 이메일만 같은 구글 계정을 가진 사람이 남의 계정에 들어갈 수 있다.
     */
    private GoogleLoginResponse linkRequired(User user, GoogleUserInfo google) {
        // 비밀번호가 없는 구글 전용 계정은 비밀번호로 본인 확인을 할 수 없고,
        // 이미 다른 구글 계정이 연결된 회원은 연결하는 순간 기존 연결이 덮어써진다. 둘 다 이 흐름으로는 연결하지 않는다
        if (!user.hasPassword() || user.isGoogleLinked()) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS, "같은 이메일 계정에 다른 구글 계정이 연결됨");
        }
        String linkToken = googleLinkTokenService.issue(user.getId(), google.sub(), google.email());
        return GoogleLoginResponse.linkRequired(linkToken, user.getEmail());
    }

    @Transactional
    public GoogleLinkResponse link(GoogleLinkRequest request, String userAgent) {
        LinkData link = googleLinkTokenService.find(request.linkToken())
                .orElseThrow(GoogleAuthService::expiredLinkToken);
        User user = getLinkTarget(link.memberId());
        String email = user.getEmail();

        // 비밀번호 실패는 이메일 로그인과 같은 login:fail:{email}에 합산한다. 잠겨 있으면 비밀번호를 확인하지 않는다(423)
        // 비밀번호를 비교하기 전에 이번 시도를 먼저 기록해서, 동시에 여러 번 보내도 5번까지만 비교한다
        LoginAttempt attempt = loginAttemptService.start(email);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 이번 실패가 5번째면 여기서 423이 나간다
            loginAttemptService.recordFailure(attempt);
            throw new CustomException(ErrorCode.INVALID_LINK_PASSWORD);
        }
        // 비밀번호로 본인 확인은 끝났으니 실패 기록(과 방금 기록한 시도)을 지운다. 아래 409로 끝나도 비밀번호는 맞았다
        loginAttemptService.reset(email);

        // linkToken을 받은 뒤 10분 사이에 이 구글 계정이 다른 회원에게 연결됐거나(마이페이지 연결 등),
        // 이 회원에게 다른 구글 계정이 연결됐을 수 있다
        if (user.isGoogleLinked() || userService.findByGoogleSub(link.googleSub()).isPresent()) {
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }
        try {
            userService.linkGoogle(user, link.googleSub(), link.googleEmail());
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 다른 요청이 같은 구글 계정을 먼저 연결한 경우 (google_sub UNIQUE 위반)
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }

        googleLinkTokenService.delete(request.linkToken());
        return GoogleLinkResponse.of(tokenService.issue(user, userAgent), user.getOnboardingStatus());
    }

    // 연결 대기 중에 회원이 탈퇴해서 없으면 linkToken 만료와 똑같이 401로 응답한다
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
