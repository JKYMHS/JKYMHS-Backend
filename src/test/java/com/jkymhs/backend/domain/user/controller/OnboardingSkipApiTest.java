package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingSkipApiTest extends MemberApiTestSupport {

    @Test
    @DisplayName("NOT_STARTED에서 건너뛰면 SKIPPED가 되고, 다시 건너뛰어도 SKIPPED다")
    void skip() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(auth(post("/api/members/me/onboarding/skip"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.onboardingStatus").value("SKIPPED"));
        }
        assertThat(userRepository.findById(user.getId()).orElseThrow().getOnboardingStatus())
                .isEqualTo(OnboardingStatus.SKIPPED);
    }

    @Test
    @DisplayName("취향을 이미 저장했으면(DONE) 409 ONBOARDING_ALREADY_DONE이고 상태는 DONE 그대로다")
    void alreadyDone() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);
        mockMvc.perform(json(put("/api/members/me/preferences"), token, """
                        {"styleIds": ["rest"], "pace": "relaxed", "timeOfDay": "morning", "accessible": false, "pet": false}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(auth(post("/api/members/me/onboarding/skip"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_DONE"))
                .andExpect(jsonPath("$.message").value("이미 취향 설정을 완료했습니다."));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getOnboardingStatus())
                .isEqualTo(OnboardingStatus.DONE);
    }

    @Test
    @DisplayName("건너뛴 회원도 나중에 취향을 저장하면 DONE이 된다")
    void saveAfterSkip() throws Exception {
        String token = accessToken(saveEmailUser());
        mockMvc.perform(auth(post("/api/members/me/onboarding/skip"), token)).andExpect(status().isOk());

        mockMvc.perform(json(put("/api/members/me/preferences"), token, """
                        {"styleIds": ["nature"], "pace": "relaxed", "timeOfDay": "morning", "accessible": false, "pet": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("DONE"));
    }
}
