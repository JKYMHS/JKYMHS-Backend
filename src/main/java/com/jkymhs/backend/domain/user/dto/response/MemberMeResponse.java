package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.User;

import java.time.LocalDateTime;

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

    public record Google(boolean linked, String email) {

        public static Google from(User user) {
            return new Google(user.isGoogleLinked(), user.getGoogleEmail());
        }
    }
}
