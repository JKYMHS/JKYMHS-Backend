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

    /**
     * 가입할 때 모든 항목을 저장한다. agreedTypes에 없는 항목은 동의하지 않은 것(agreed = false)으로 저장한다.
     */
    @Transactional
    public void saveInitialConsents(User user, Set<ConsentType> agreedTypes) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserConsent> consents = Arrays.stream(ConsentType.values())
                .map(type -> UserConsent.create(user, type, agreedTypes.contains(type), now))
                .toList();
        userConsentRepository.saveAll(consents);
    }

    /**
     * 모든 항목의 현재 상태. 행이 없는 항목(구글 가입 회원, 새로 생긴 항목)은 동의하지 않은 것으로 본다.
     */
    public List<ConsentState> getConsents(Long userId) {
        Map<ConsentType, UserConsent> saved = findByType(userId);
        return Arrays.stream(ConsentType.values())
                .map(type -> ConsentState.of(type, saved.get(type)))
                .toList();
    }

    /**
     * 받은 항목만 바꾸고, 바꾼 항목의 상태를 항목 순서대로 돌려준다.
     *
     * 필수 항목을 끄는 요청은 하나라도 있으면 아무것도 바꾸지 않고 400이다 (일부만 반영되면 FE 토글 상태와 서버가 어긋난다).
     * 필수 항목을 켜는 요청은 받는다. 구글로 가입한 회원은 가입 API가 동의를 받지 않아서, FE가 가입 직후 필수 3개를 true로 보내 저장한다.
     *
     * 행동 데이터 동의를 끄면 같은 트랜잭션에서 {@link BehaviorConsentRevokedEvent}를 발행한다. 행동 데이터 도메인이 동기 리스너로
     * 쌓인 행동 기록·취향 프로필을 바로 지운다. 삭제가 실패하면 동의 변경까지 롤백되고 500이 나가서 FE가 토글을 되돌린다.
     * 이미 꺼져 있어도 다시 발행한다. 지우는 쪽은 여러 번 지워도 결과가 같고, 혹시 남아 있던 기록도 이번에 지워진다.
     */
    @Transactional
    public List<ConsentState> updateConsents(Long userId, Map<ConsentType, Boolean> changes) {
        changes.forEach((type, agreed) -> {
            if (type.isRequired() && !agreed) {
                throw new CustomException(ErrorCode.REQUIRED_CONSENT_CANNOT_REVOKE, "필수 약관 철회 요청: " + type);
            }
        });

        // 같은 회원의 동의 변경이 동시에 와서 없는 행을 둘 다 INSERT하지 않게 회원 행을 잠근다
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
        // EnumMap은 enum 선언 순서로 돈다
        return changed.entrySet().stream()
                .map(entry -> ConsentState.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 다른 도메인이 동의 여부를 확인할 때 쓴다 (행동 데이터 수집, 방문 확인 메일 등). 행이 없으면 false.
     */
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

    /**
     * 항목 하나의 상태. 행이 없으면 agreed = false, agreedAt = null.
     */
    public record ConsentState(ConsentType type, boolean required, boolean agreed, LocalDateTime agreedAt) {

        static ConsentState of(ConsentType type, UserConsent consent) {
            if (consent == null) {
                return new ConsentState(type, type.isRequired(), false, null);
            }
            return new ConsentState(type, type.isRequired(), consent.isAgreed(), consent.getAgreedAt());
        }
    }
}
