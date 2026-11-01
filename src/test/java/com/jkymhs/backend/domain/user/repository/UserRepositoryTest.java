package com.jkymhs.backend.domain.user.repository;

import com.jkymhs.backend.TestcontainersConfiguration;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 MySQL(Testcontainers)에 저장해 보면서 엔티티 매핑을 확인한다.
 * {@code @DataJpaTest}는 JPA 관련 빈만 띄워서 JpaAuditingConfig를 직접 Import해야 createdAt이 채워진다.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("회원을 저장하면 온보딩 상태는 NOT_STARTED, 생성·수정 시각은 자동으로 채워진다")
    void saveUser() {
        User saved = userRepository.saveAndFlush(User.createWithPassword("user@example.com", "hashed"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getGoogleSub()).isNull();
    }

    @Test
    @DisplayName("이메일로 회원을 찾는다")
    void findByEmail() {
        userRepository.save(User.createWithPassword("user@example.com", "hashed"));

        assertThat(userRepository.findByEmail("user@example.com")).isPresent();
        assertThat(userRepository.findByEmail("other@example.com")).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일로 두 번 저장하면 UNIQUE 제약에 걸린다")
    void duplicateEmail() {
        userRepository.saveAndFlush(User.createWithPassword("user@example.com", "hashed"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.createWithPassword("user@example.com", "hashed2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
