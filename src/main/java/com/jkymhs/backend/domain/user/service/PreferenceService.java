package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.entity.UserPreference;
import com.jkymhs.backend.domain.user.entity.UserTravelStyle;
import com.jkymhs.backend.domain.user.repository.UserPreferenceRepository;
import com.jkymhs.backend.domain.user.repository.UserTravelStyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 취향(온보딩) 저장·조회·건너뛰기와, 다른 도메인이 쓰는 "적용할 취향" 계산.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceService {

    private final UserService userService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserTravelStyleRepository userTravelStyleRepository;

    /**
     * 취향 전체를 저장하고 온보딩을 끝낸다 (PUT: 보낸 값으로 통째로 바꾼다). 온보딩과 마이페이지가 같이 쓴다.
     * 회원 행을 잠가서 같은 회원의 저장이 동시에 와도 취향 행이 두 개 생기지 않게 한다 (user_id UNIQUE 위반 500 방지).
     */
    @Transactional
    public PreferenceView save(Long userId, List<TravelStyle> styles, Pace pace, TimeOfDay timeOfDay,
                               boolean accessible, boolean pet) {
        userService.getByIdForUpdate(userId);

        // 기존 스타일을 먼저 지운다. 이 DELETE가 1차 캐시를 비우므로, 회원은 지운 뒤에 다시 읽어서 쓴다 (잠금은 트랜잭션 끝까지 유지된다)
        userTravelStyleRepository.deleteAllByUserId(userId);
        User user = userService.getById(userId);

        Optional<UserPreference> saved = userPreferenceRepository.findByUserId(userId);
        if (saved.isPresent()) {
            saved.get().update(pace, timeOfDay, accessible, pet);
        } else {
            userPreferenceRepository.save(UserPreference.create(user, pace, timeOfDay, accessible, pet));
        }
        userTravelStyleRepository.saveAll(styles.stream().map(style -> UserTravelStyle.create(user, style)).toList());
        user.completeOnboarding();
        return new PreferenceView(OnboardingStatus.DONE, styles, pace, timeOfDay, accessible, pet);
    }

    /**
     * 저장한 취향. 온보딩을 끝내지 않았으면(NOT_STARTED·SKIPPED) 상태만 있고 나머지는 null이다.
     */
    public PreferenceView get(Long userId) {
        User user = userService.getById(userId);
        if (user.getOnboardingStatus() != OnboardingStatus.DONE) {
            return PreferenceView.empty(user.getOnboardingStatus());
        }
        return userPreferenceRepository.findByUserId(userId)
                .map(preference -> new PreferenceView(OnboardingStatus.DONE, findStyles(userId),
                        preference.getPace(), preference.getTimeOfDay(), preference.isAccessible(), preference.isPet()))
                .orElseGet(() -> PreferenceView.empty(OnboardingStatus.DONE));
    }

    /**
     * 온보딩 "나중에 하기". 이미 취향을 저장했으면(DONE) 409.
     * 건너뛴 회원의 기본값은 저장하지 않고 쓸 때 채운다 ({@link #getEffective}).
     */
    @Transactional
    public OnboardingStatus skipOnboarding(Long userId) {
        User user = userService.getByIdForUpdate(userId);
        user.skipOnboarding();
        return user.getOnboardingStatus();
    }

    /**
     * 추천 등에 실제로 적용할 취향. 온보딩을 끝내지 않은 회원은 모든 스타일·여유롭게·오전(10:00)·편의/반려동물 없음이다.
     */
    public EffectivePreference getEffective(Long userId) {
        User user = userService.getById(userId);
        if (user.getOnboardingStatus() != OnboardingStatus.DONE) {
            return EffectivePreference.defaults(user.getOnboardingStatus());
        }
        Optional<UserPreference> preference = userPreferenceRepository.findByUserId(userId);
        if (preference.isEmpty()) {
            return EffectivePreference.defaults(OnboardingStatus.DONE);
        }
        List<TravelStyle> styles = findStyles(userId);
        Set<TravelStyle> styleSet = styles.isEmpty() ? Set.of() : EnumSet.copyOf(styles);
        UserPreference p = preference.get();
        return new EffectivePreference(styleSet, p.getPace(), p.getTimeOfDay(), p.isAccessible(), p.isPet(),
                OnboardingStatus.DONE);
    }

    // 탈퇴할 때 취향과 여행 스타일을 지운다
    @Transactional
    public void deleteAll(Long userId) {
        userTravelStyleRepository.deleteAllByUserId(userId);
        userPreferenceRepository.deleteAllByUserId(userId);
    }

    private List<TravelStyle> findStyles(Long userId) {
        return userTravelStyleRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .map(UserTravelStyle::getStyle)
                .toList();
    }

    /**
     * 취향 조회·저장 응답에 쓰는 값. 온보딩을 끝내지 않았으면 status 외에는 null이다.
     */
    public record PreferenceView(
            OnboardingStatus status,
            List<TravelStyle> styles,
            Pace pace,
            TimeOfDay timeOfDay,
            Boolean accessible,
            Boolean pet
    ) {

        static PreferenceView empty(OnboardingStatus status) {
            return new PreferenceView(status, null, null, null, null, null);
        }
    }
}
