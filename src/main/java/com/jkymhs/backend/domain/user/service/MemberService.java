package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.auth.client.GoogleOAuthClient;
import com.jkymhs.backend.domain.auth.client.GoogleUserInfo;
import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.dto.response.GoogleAccountLinkResponse;
import com.jkymhs.backend.domain.user.dto.response.MemberMeResponse;
import com.jkymhs.backend.domain.user.dto.request.WithdrawRequest;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 마이페이지 계정 관리: 내 정보·이름, 구글 연결/해제, 비밀번호 변경/설정, 탈퇴.
 *
 * 클래스 전체에 {@code @Transactional}을 걸지 않았다. 구글 연결과 구글 회원 탈퇴는 구글 서버를 부르는데,
 * 그동안 트랜잭션(DB 커넥션·회원 행 잠금)을 잡고 있으면 구글이 느려질 때 커넥션 풀이 바닥난다.
 * 그래서 구글 호출은 트랜잭션 밖에서 끝내고, DB 작업만 트랜잭션으로 묶는다 (GoogleAuthService와 같은 방식).
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserService userService;
    private final UserConsentService userConsentService;
    private final PreferenceService preferenceService;
    private final TokenService tokenService;
    private final GoogleOAuthClient googleOAuthClient;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public MemberMeResponse getMe(Long userId) {
        return MemberMeResponse.from(userService.getById(userId));
    }

    @Transactional
    public MemberMeResponse changeNickname(Long userId, String nickname) {
        User user = userService.getById(userId);
        user.changeNickname(nickname);
        return MemberMeResponse.from(user);
    }

    /**
     * 로그인한 회원에게 구글 계정을 연결한다. 로그인 이메일과 다른 구글 계정도 연결할 수 있다.
     * 이미 구글이 연결된 회원(같은 구글 계정 포함)이거나, 그 구글 계정이 다른 회원에게 연결돼 있으면 409.
     * 인가 코드는 한 번만 쓸 수 있어서, 이미 연결된 회원이면 구글을 부르기 전에 먼저 거절한다.
     */
    public GoogleAccountLinkResponse linkGoogle(Long userId, String code, String redirectUri) {
        if (userService.getById(userId).isGoogleLinked()) {
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED, "이미 구글 계정이 연결된 회원");
        }
        GoogleUserInfo google = googleOAuthClient.fetchUserInfo(code, redirectUri);
        if (userService.findByGoogleSub(google.sub()).isPresent()) {
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }
        try {
            User user = userService.linkGoogle(userId, google.sub(), google.email());
            return new GoogleAccountLinkResponse(MemberMeResponse.Google.from(user));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 다른 회원이 같은 구글 계정을 먼저 연결한 경우 (google_sub UNIQUE 위반)
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }
    }

    /**
     * 구글 연결 해제. 비밀번호가 없는 회원이 해제하면 로그인할 방법이 없어져서 막는다 (먼저 비밀번호 설정).
     * 구글 캘린더 연동은 별개라서 그대로 둔다.
     */
    @Transactional
    public void unlinkGoogle(Long userId) {
        User user = userService.getByIdForUpdate(userId);
        if (!user.isGoogleLinked()) {
            throw new CustomException(ErrorCode.GOOGLE_NOT_LINKED);
        }
        if (!user.hasPassword()) {
            throw new CustomException(ErrorCode.PASSWORD_REQUIRED_FOR_UNLINK);
        }
        user.unlinkGoogle();
    }

    /**
     * 비밀번호 변경. 성공하면 모든 기기의 로그인(리프레시 토큰)을 끊고, 지금 기기에 새 토큰을 발급한다.
     * 리프레시 토큰 쿠키는 Path=/api/auth라서 이 요청에는 오지 않는다. 그래서 "지금 기기의 토큰만 남기기"가 불가능해서
     * 전부 끊고 새로 준다 (비밀번호가 유출돼 바꾸는 경우라면 다른 기기도 끊기는 편이 안전하다).
     */
    @Transactional
    public IssuedTokens changePassword(Long userId, String currentPassword, String newPassword, String userAgent) {
        User user = userService.getByIdForUpdate(userId);
        if (!user.hasPassword()) {
            throw new CustomException(ErrorCode.GOOGLE_ONLY_ACCOUNT,
                    "비밀번호가 없는 계정입니다. 비밀번호 설정을 이용해주세요.", "구글 전용 계정의 비밀번호 변경");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_INPUT,
                    "현재 비밀번호와 다른 비밀번호를 입력해주세요.", "현재 비밀번호와 같은 새 비밀번호");
        }
        user.changePassword(passwordEncoder.encode(newPassword));

        // revokeAll의 DELETE가 먼저 새 비밀번호를 DB에 쓰고(flush) 1차 캐시를 비운다. 토큰은 다시 읽은 회원으로 발급한다
        tokenService.revokeAll(userId);
        return tokenService.issue(userService.getById(userId), userAgent);
    }

    /**
     * 구글로만 가입한 회원의 첫 비밀번호 설정. 이후에는 이메일·비밀번호 로그인도 된다.
     */
    @Transactional
    public void setPassword(Long userId, String newPassword) {
        User user = userService.getByIdForUpdate(userId);
        if (user.hasPassword()) {
            throw new CustomException(ErrorCode.PASSWORD_ALREADY_SET);
        }
        user.setInitialPassword(passwordEncoder.encode(newPassword));
    }

    /**
     * 회원 탈퇴. 본인 확인 → (한 트랜잭션) 탈퇴 이벤트 발행 → 리프레시 토큰·동의·취향 삭제 → 회원 삭제.
     *
     * 본인 확인: 비밀번호가 있는 회원은 비밀번호, 구글로만 가입한 회원은 구글 재인증(인가 코드의 sub가 연결된 구글 계정과 같아야 함).
     * 구글 재인증은 트랜잭션 밖에서 먼저 끝낸다 (클래스 주석).
     *
     * {@link UserWithdrawnEvent}는 같은 트랜잭션의 동기 리스너가 받는다. 찜·여행·추천·캘린더 도메인이 회원을 참조하는 행을
     * 지우므로, 회원 행을 지우기 전에 발행해야 FK 위반이 나지 않는다. 리스너 하나라도 실패하면 탈퇴 전체가 롤백된다.
     */
    public void withdraw(Long userId, WithdrawRequest request) {
        WithdrawRequest body = request == null ? WithdrawRequest.EMPTY : request;
        User user = userService.getById(userId);
        boolean googleVerified = !user.hasPassword() && verifyGoogleIdentity(user, body);

        transactionTemplate.executeWithoutResult(status -> {
            User locked = userService.getByIdForUpdate(userId);
            if (locked.hasPassword()) {
                verifyPassword(locked, body.password());
            } else if (!googleVerified) {
                throw identityVerificationFailed("본인 확인 수단 없음");
            }

            eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
            tokenService.revokeAll(userId);
            userConsentService.deleteAll(userId);
            preferenceService.deleteAll(userId);
            userService.delete(userId);
        });
    }

    private void verifyPassword(User user, String password) {
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw identityVerificationFailed("비밀번호 없음 또는 불일치");
        }
    }

    /**
     * 구글 재인증. 인가 코드가 거부되면(만료·재사용 등) 401 본인 확인 실패다.
     * 구글 서버 장애(502)는 사용자가 잘못한 게 아니라서 그대로 502로 둔다 (잠시 후 다시 시도하면 된다).
     */
    private boolean verifyGoogleIdentity(User user, WithdrawRequest body) {
        if (!StringUtils.hasText(body.googleCode()) || !StringUtils.hasText(body.redirectUri())) {
            throw identityVerificationFailed("구글 인가 코드 없음");
        }
        GoogleUserInfo google;
        try {
            google = googleOAuthClient.fetchUserInfo(body.googleCode(), body.redirectUri());
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.GOOGLE_AUTH_FAILED) {
                throw identityVerificationFailed("구글 재인증 실패");
            }
            throw e;
        }
        if (!google.sub().equals(user.getGoogleSub())) {
            throw identityVerificationFailed("연결된 구글 계정과 다른 계정");
        }
        return true;
    }

    private static CustomException identityVerificationFailed(String description) {
        return new CustomException(ErrorCode.IDENTITY_VERIFICATION_FAILED, description);
    }
}
