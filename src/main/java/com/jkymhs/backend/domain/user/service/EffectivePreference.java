package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;

import java.util.Set;

public record EffectivePreference(
        Set<TravelStyle> styles,
        Pace pace,
        TimeOfDay timeOfDay,
        boolean accessible,
        boolean pet,
        OnboardingStatus status
) {

    public static final Pace DEFAULT_PACE = Pace.RELAXED;
    public static final TimeOfDay DEFAULT_TIME_OF_DAY = TimeOfDay.MORNING;

    static EffectivePreference defaults(OnboardingStatus status) {
        return new EffectivePreference(Set.of(), DEFAULT_PACE, DEFAULT_TIME_OF_DAY, false, false, status);
    }
}
