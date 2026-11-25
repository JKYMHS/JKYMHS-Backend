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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceService {

    private final UserService userService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserTravelStyleRepository userTravelStyleRepository;

    @Transactional
    public PreferenceView save(Long userId, List<TravelStyle> styles, Pace pace, TimeOfDay timeOfDay,
                               boolean accessible, boolean pet) {
        userService.getByIdForUpdate(userId);

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

    @Transactional
    public OnboardingStatus skipOnboarding(Long userId) {
        User user = userService.getByIdForUpdate(userId);
        user.skipOnboarding();
        return user.getOnboardingStatus();
    }

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
