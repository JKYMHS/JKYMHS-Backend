package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberMeApiTest extends MemberApiTestSupport {

    @Test
    @DisplayName("내 정보: 이름을 정하지 않았으면 이메일 앞부분을 이름으로 주고, 구글 미연결이면 google.email은 null이다")
    void meWithEmailUser() throws Exception {
        User user = saveEmailUser();

        mockMvc.perform(auth(get("/api/members/me"), accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value("minji"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.google.linked").value(false))
                .andExpect(jsonPath("$.google.email").doesNotExist())
                .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.calendarLinked").doesNotExist());
    }

    @Test
    @DisplayName("내 정보: 구글로만 가입한 회원은 hasPassword가 false이고 구글 이메일을 준다")
    void meWithGoogleOnlyUser() throws Exception {
        User user = saveGoogleOnlyUser();

        mockMvc.perform(auth(get("/api/members/me"), accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.google.linked").value(true))
                .andExpect(jsonPath("$.google.email").value(GOOGLE_EMAIL));
    }

    @Test
    @DisplayName("토큰 없이 부르면 401 UNAUTHORIZED")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("이름 변경: 앞뒤 공백을 빼고 저장하고, 바뀐 내 정보를 준다")
    void changeNickname() throws Exception {
        User user = saveEmailUser();

        mockMvc.perform(json(patch("/api/members/me"), accessToken(user), """
                        {"nickname": "  김민지  "}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("김민지"))
                .andExpect(jsonPath("$.email").value(EMAIL));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("김민지");
    }

    @Test
    @DisplayName("이름 변경: 공백뿐이거나 30자를 넘으면 400")
    void invalidNickname() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);

        for (String nickname : new String[]{"   ", "가".repeat(31)}) {
            mockMvc.perform(json(patch("/api/members/me"), token, """
                            {"nickname": "%s"}
                            """.formatted(nickname)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("이름은 1~30자로 입력해주세요."));
        }
        // 30자는 된다
        mockMvc.perform(json(patch("/api/members/me"), token, """
                        {"nickname": "%s"}
                        """.formatted("가".repeat(30))))
                .andExpect(status().isOk());
    }
}
