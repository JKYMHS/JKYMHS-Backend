package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleUnlinkApiTest extends MemberApiTestSupport {

    @Test
    @DisplayName("비밀번호가 있는 회원은 구글 연결을 해제할 수 있다")
    void unlink() throws Exception {
        User user = saveEmailUser();
        user.linkGoogle(GOOGLE_SUB, GOOGLE_EMAIL);
        userRepository.save(user);

        mockMvc.perform(auth(delete("/api/members/me/google"), accessToken(user)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleLinked()).isFalse();
    }

    @Test
    @DisplayName("구글로만 가입한 회원(비밀번호 없음)은 409 PASSWORD_REQUIRED_FOR_UNLINK이고 연결이 유지된다")
    void passwordRequired() throws Exception {
        User user = saveGoogleOnlyUser();

        mockMvc.perform(auth(delete("/api/members/me/google"), accessToken(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED_FOR_UNLINK"))
                .andExpect(jsonPath("$.message").value("비밀번호를 먼저 설정해야 구글 연결을 해제할 수 있습니다."));

        assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleLinked()).isTrue();
    }

    @Test
    @DisplayName("구글이 연결돼 있지 않으면 404 GOOGLE_NOT_LINKED")
    void notLinked() throws Exception {
        mockMvc.perform(auth(delete("/api/members/me/google"), accessToken(saveEmailUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOOGLE_NOT_LINKED"))
                .andExpect(jsonPath("$.message").value("연결된 구글 계정이 없습니다."));
    }
}
