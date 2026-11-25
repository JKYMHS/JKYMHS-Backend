package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import com.jkymhs.backend.domain.user.service.PreferenceService.PreferenceView;

import java.util.List;

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
