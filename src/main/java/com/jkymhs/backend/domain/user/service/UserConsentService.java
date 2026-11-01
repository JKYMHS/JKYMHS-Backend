package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.entity.UserConsent;
import com.jkymhs.backend.domain.user.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserConsentService {

    private final UserConsentRepository userConsentRepository;

    /**
     * 가입할 때 5개 항목을 모두 저장한다. agreedTypes에 없는 항목은 동의하지 않은 것(agreed = false)으로 저장한다.
     */
    @Transactional
    public void saveInitialConsents(User user, Set<ConsentType> agreedTypes) {
        LocalDateTime now = LocalDateTime.now();
        List<UserConsent> consents = Arrays.stream(ConsentType.values())
                .map(type -> UserConsent.create(user, type, agreedTypes.contains(type), now))
                .toList();
        userConsentRepository.saveAll(consents);
    }
}
