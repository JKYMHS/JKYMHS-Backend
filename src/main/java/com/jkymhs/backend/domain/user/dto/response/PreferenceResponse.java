package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import com.jkymhs.backend.domain.user.service.PreferenceService.PreferenceView;

import java.util.List;

/**
 * 취향 저장·조회 응답. 온보딩을 끝내지 않았으면(NOT_STARTED·SKIPPED) onboardingStatus 외에는 모두 null이다.
 * pace·timeOfDay는 소문자 코드로 나간다 (CodeEnum).
 */
public record PreferenceResponse(
        OnboardingStatus onboardingStatus,
        List<String> styleIds,
        Pace pace,
        TimeOfDay timeOfDay,
        Boolean accessible,
        Boolean pet
) {

    public static PreferenceResponse from(PreferenceView view) {
        List<String> styleIds = view.styles() == null ? null : view.styles().stream().map(TravelStyle::code).toList();
        return new PreferenceResponse(view.status(), styleIds, view.pace(), view.timeOfDay(), view.accessible(), view.pet());
    }
}
