package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;

import java.util.Set;

/**
 * 추천 등 다른 도메인이 쓰는 "실제로 적용할" 취향. 온보딩을 하지 않았거나 건너뛴 회원은 기본값으로 채운다.
 *
 * @param styles 고른 여행 스타일. 비어 있으면 "모든 스타일"이라는 뜻이다 (건너뛴 회원)
 * @param status 회원의 온보딩 상태. DONE이 아니면 나머지 값은 모두 기본값이다
 */
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
