package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.Companion;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 다른 도메인(추천·행동 데이터 등)이 부르는 회원 도메인 메서드.
 */
class MemberContractTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserConsentService userConsentService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("마지막으로 고른 동행을 기억하고 돌려준다. 고른 적이 없으면 비어 있다")
    void lastCompanion() {
        User user = userRepository.save(User.createWithPassword("a@example.com", "hash"));
        assertThat(userService.getLastCompanion(user.getId())).isEmpty();

        userService.rememberCompanion(user.getId(), Companion.KIDS);

        assertThat(userService.getLastCompanion(user.getId())).contains(Companion.KIDS);
    }

    @Test
    @DisplayName("잠금 조회는 없는 회원이면 404 RESOURCE_NOT_FOUND")
    void getByIdForUpdateNotFound() {
        assertThatThrownBy(() -> userService.getByIdForUpdate(999L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("동의 여부: 동의한 항목만 true, 미동의·행 없음은 false")
    void isAgreed() {
        User user = userRepository.save(User.createWithPassword("a@example.com", "hash"));
        User noRows = userRepository.save(User.createWithGoogle("g@gmail.com", "sub", "g@gmail.com"));
        userConsentService.saveInitialConsents(user, EnumSet.of(ConsentType.BEHAVIOR_DATA));

        assertThat(userConsentService.isAgreed(user.getId(), ConsentType.BEHAVIOR_DATA)).isTrue();
        assertThat(userConsentService.isAgreed(user.getId(), ConsentType.EMAIL_NOTIFICATION)).isFalse();
        assertThat(userConsentService.isAgreed(noRows.getId(), ConsentType.BEHAVIOR_DATA)).isFalse();
    }
}
