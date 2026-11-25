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
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }
    }

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

        tokenService.revokeAll(userId);
        return tokenService.issue(userService.getById(userId), userAgent);
    }

    @Transactional
    public void setPassword(Long userId, String newPassword) {
        User user = userService.getByIdForUpdate(userId);
        if (user.hasPassword()) {
            throw new CustomException(ErrorCode.PASSWORD_ALREADY_SET);
        }
        user.setInitialPassword(passwordEncoder.encode(newPassword));
    }

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
