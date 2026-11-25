package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentSaveLogRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import com.jkymhs.backend.domain.behavior.repository.PreferenceProfileRepository;
import com.jkymhs.backend.global.event.BehaviorConsentRevokedEvent;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BehaviorDataCleaner {

    private final BehaviorEventRepository behaviorEventRepository;
    private final PreferenceProfileRepository preferenceProfileRepository;
    private final ContentViewRepository contentViewRepository;
    private final ContentSaveLogRepository contentSaveLogRepository;

    @EventListener
    @Transactional
    public void onConsentRevoked(BehaviorConsentRevokedEvent event) {
        deleteProfileData(event.userId());
    }

    @EventListener
    @Transactional
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        deleteProfileData(event.userId());
        contentViewRepository.deleteAllByUserId(event.userId());
        contentSaveLogRepository.deleteAllByUserId(event.userId());
    }

    private void deleteProfileData(Long userId) {
        behaviorEventRepository.deleteAllByUserId(userId);
        preferenceProfileRepository.deleteAllByUserId(userId);
    }
}
