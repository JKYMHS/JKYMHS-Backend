package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.entity.UserConsent;
import com.jkymhs.backend.domain.user.repository.UserConsentRepository;
import com.jkymhs.backend.global.event.BehaviorConsentRevokedEvent;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserConsentService {

    private final UserConsentRepository userConsentRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void saveInitialConsents(User user, Set<ConsentType> agreedTypes) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserConsent> consents = Arrays.stream(ConsentType.values())
                .map(type -> UserConsent.create(user, type, agreedTypes.contains(type), now))
                .toList();
        userConsentRepository.saveAll(consents);
    }

    public List<ConsentState> getConsents(Long userId) {
        Map<ConsentType, UserConsent> saved = findByType(userId);
        return Arrays.stream(ConsentType.values())
                .map(type -> ConsentState.of(type, saved.get(type)))
                .toList();
    }

    @Transactional
    public List<ConsentState> updateConsents(Long userId, Map<ConsentType, Boolean> changes) {
        changes.forEach((type, agreed) -> {
            if (type.isRequired() && !agreed) {
                throw new CustomException(ErrorCode.REQUIRED_CONSENT_CANNOT_REVOKE, "필수 약관 철회 요청: " + type);
            }
        });

        User user = userService.getByIdForUpdate(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<ConsentType, UserConsent> saved = findByType(userId);

        Map<ConsentType, UserConsent> changed = new EnumMap<>(ConsentType.class);
        changes.forEach((type, agreed) -> {
            UserConsent consent = saved.get(type);
            if (consent == null) {
                consent = userConsentRepository.save(UserConsent.create(user, type, agreed, now));
            } else {
                consent.change(agreed, now);
            }
            changed.put(type, consent);
        });

        if (Boolean.FALSE.equals(changes.get(ConsentType.BEHAVIOR_DATA))) {
            eventPublisher.publishEvent(new BehaviorConsentRevokedEvent(userId));
        }
        return changed.entrySet().stream()
                .map(entry -> ConsentState.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    public boolean isAgreed(Long userId, ConsentType type) {
        UserConsent consent = findByType(userId).get(type);
        return consent != null && consent.isAgreed();
    }

    @Transactional
    public void deleteAll(Long userId) {
        userConsentRepository.deleteAllByUserId(userId);
    }

    private Map<ConsentType, UserConsent> findByType(Long userId) {
        Map<ConsentType, UserConsent> byType = new EnumMap<>(ConsentType.class);
        userConsentRepository.findAllByUserId(userId).forEach(consent -> byType.put(consent.getConsentType(), consent));
        return byType;
    }

    public record ConsentState(ConsentType type, boolean required, boolean agreed, LocalDateTime agreedAt) {

        static ConsentState of(ConsentType type, UserConsent consent) {
            if (consent == null) {
                return new ConsentState(type, type.isRequired(), false, null);
            }
            return new ConsentState(type, type.isRequired(), consent.isAgreed(), consent.getAgreedAt());
        }
    }
}
