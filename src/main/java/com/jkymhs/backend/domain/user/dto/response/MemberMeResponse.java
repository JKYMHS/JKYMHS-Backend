package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 내 정보. nickname은 정하지 않았으면 이메일 앞부분이다 (FE가 따로 대체 문구를 만들지 않아도 되게).
 * hasPassword = false면 구글로만 가입한 계정이라 FE가 "비밀번호 변경" 대신 "비밀번호 설정"을 보여준다.
 */
public record MemberMeResponse(
        Long id,
        String email,
        String nickname,
        boolean hasPassword,
        Google google,
        OnboardingStatus onboardingStatus,
        LocalDateTime createdAt
) {

    public static MemberMeResponse from(User user) {
        return new MemberMeResponse(
                user.getId(),
                user.getEmail(),
                user.displayName(),
                user.hasPassword(),
                Google.from(user),
                user.getOnboardingStatus(),
                user.getCreatedAt()
        );
    }

    // 구글 계정 연결 상태. 연결하지 않았으면 email은 null
    public record Google(boolean linked, String email) {

        public static Google from(User user) {
            return new Google(user.isGoogleLinked(), user.getGoogleEmail());
        }
    }
}
