package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("구글 계정을 연결하면 sub와 구글 이메일이 저장되고, sub로 회원을 찾을 수 있다")
    void linkGoogle() {
        User user = userService.register(User.createWithPassword("user@example.com", "hashed"));

        userService.linkGoogle(user, "google-sub", "user@gmail.com");

        User found = userService.findByGoogleSub("google-sub").orElseThrow();
        assertThat(found.getId()).isEqualTo(user.getId());
        assertThat(found.getGoogleEmail()).isEqualTo("user@gmail.com");
    }

    @Test
    @DisplayName("다른 회원이 이미 쓰는 구글 sub로 연결하면 그 자리에서 UNIQUE 위반 예외가 난다 (커밋까지 미루지 않는다)")
    void linkGoogleDuplicateSub() {
        userService.register(User.createWithGoogle("other@gmail.com", "google-sub", "other@gmail.com"));
        User user = userService.register(User.createWithPassword("user@example.com", "hashed"));

        assertThatThrownBy(() -> userService.linkGoogle(user, "google-sub", "other@gmail.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleLinked()).isFalse();
    }
}
