package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.entity.UserTravelStyle;
import com.jkymhs.backend.domain.user.repository.UserPreferenceRepository;
import com.jkymhs.backend.domain.user.repository.UserTravelStyleRepository;
import com.jkymhs.backend.domain.user.service.EffectivePreference;
import com.jkymhs.backend.domain.user.service.PreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreferenceApiTest extends MemberApiTestSupport {

    private static final String VALID_BODY = """
            {"styleIds": ["culture", "food", "nature"], "pace": "packed", "timeOfDay": "early",
             "accessible": true, "pet": false}
            """;

    @Autowired
    private PreferenceService preferenceService;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private UserTravelStyleRepository userTravelStyleRepository;

    @Test
    @DisplayName("취향을 저장하면 온보딩이 DONE이 되고, 보낸 값을 소문자 코드 그대로 돌려준다")
    void save() throws Exception {
        User user = saveEmailUser();

        save(accessToken(user), VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("DONE"))
                .andExpect(jsonPath("$.styleIds").value(contains("culture", "food", "nature")))
                .andExpect(jsonPath("$.pace").value("packed"))
                .andExpect(jsonPath("$.timeOfDay").value("early"))
                .andExpect(jsonPath("$.accessible").value(true))
                .andExpect(jsonPath("$.pet").value(false));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getOnboardingStatus())
                .isEqualTo(OnboardingStatus.DONE);
        assertThat(userPreferenceRepository.findByUserId(user.getId())).hasValueSatisfying(preference -> {
            assertThat(preference.getPace()).isEqualTo(Pace.PACKED);
            assertThat(preference.getTimeOfDay()).isEqualTo(TimeOfDay.EARLY);
        });
        assertThat(userTravelStyleRepository.findAllByUserIdOrderByIdAsc(user.getId()))
                .extracting(UserTravelStyle::getStyle)
                .containsExactly(TravelStyle.CULTURE, TravelStyle.FOOD, TravelStyle.NATURE);
    }

    @Test
    @DisplayName("다시 저장하면 통째로 바뀐다. 이전과 같은 스타일을 다시 골라도 UNIQUE에 걸리지 않고, 취향 행은 하나로 유지된다")
    void saveAgainReplaces() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);
        save(token, VALID_BODY).andExpect(status().isOk());

        save(token, """
                {"styleIds": ["food"], "pace": "relaxed", "timeOfDay": "afternoon", "accessible": false, "pet": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styleIds").value(contains("food")));

        assertThat(userPreferenceRepository.count()).isEqualTo(1);
        assertThat(userTravelStyleRepository.findAllByUserIdOrderByIdAsc(user.getId()))
                .extracting(UserTravelStyle::getStyle).containsExactly(TravelStyle.FOOD);
        mockMvc.perform(auth(get("/api/members/me/preferences"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("DONE"))
                .andExpect(jsonPath("$.styleIds").value(contains("food")))
                .andExpect(jsonPath("$.pace").value("relaxed"))
                .andExpect(jsonPath("$.timeOfDay").value("afternoon"))
                .andExpect(jsonPath("$.accessible").value(false))
                .andExpect(jsonPath("$.pet").value(true));
    }

    @Test
    @DisplayName("스타일이 0개·4개·중복이면 400 \"여행 스타일을 1~3개 선택해주세요.\"이고 아무것도 저장하지 않는다")
    void invalidStyleCount() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);

        for (String styles : new String[]{"[]", "[\"rest\",\"food\",\"nature\",\"culture\"]", "[\"rest\",\"rest\"]", "null"}) {
            save(token, """
                    {"styleIds": %s, "pace": "relaxed", "timeOfDay": "morning", "accessible": false, "pet": false}
                    """.formatted(styles))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("여행 스타일을 1~3개 선택해주세요."));
        }
        assertThat(userPreferenceRepository.count()).isZero();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getOnboardingStatus())
                .isEqualTo(OnboardingStatus.NOT_STARTED);
    }

    @Test
    @DisplayName("모르는 스타일 코드나 대문자 상수 이름은 400 INVALID_INPUT")
    void unknownStyleCode() throws Exception {
        String token = accessToken(saveEmailUser());

        for (String style : new String[]{"sleep", "REST"}) {
            save(token, """
                    {"styleIds": ["%s"], "pace": "relaxed", "timeOfDay": "morning", "accessible": false, "pet": false}
                    """.formatted(style))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
        }
    }

    @Test
    @DisplayName("pet이 빠지면 false로 짐작하지 않고 400")
    void missingField() throws Exception {
        String token = accessToken(saveEmailUser());

        save(token, """
                {"styleIds": ["rest"], "pace": "relaxed", "timeOfDay": "morning", "accessible": false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("반려동물 동반 여부를 입력해주세요."));
    }

    @Test
    @DisplayName("온보딩을 건너뛴 회원의 취향 조회는 상태만 있고 나머지는 모두 null이다")
    void getWhenSkipped() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);
        mockMvc.perform(auth(post("/api/members/me/onboarding/skip"), token)).andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/members/me/preferences"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.styleIds").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.pace").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.timeOfDay").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.accessible").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.pet").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("적용할 취향: 건너뛴 회원은 모든 스타일(빈 집합)·여유롭게·오전 10시 기본값, 저장한 회원은 저장한 값")
    void effectivePreference() throws Exception {
        User skipped = userRepository.save(User.createWithPassword("skip@example.com", "hash"));
        preferenceService.skipOnboarding(skipped.getId());
        User done = saveEmailUser();
        save(accessToken(done), VALID_BODY).andExpect(status().isOk());

        EffectivePreference defaults = preferenceService.getEffective(skipped.getId());
        assertThat(defaults.status()).isEqualTo(OnboardingStatus.SKIPPED);
        assertThat(defaults.styles()).isEmpty();
        assertThat(defaults.pace()).isEqualTo(Pace.RELAXED);
        assertThat(defaults.timeOfDay()).isEqualTo(TimeOfDay.MORNING);
        assertThat(defaults.timeOfDay().startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(defaults.accessible()).isFalse();
        assertThat(defaults.pet()).isFalse();

        EffectivePreference saved = preferenceService.getEffective(done.getId());
        assertThat(saved.status()).isEqualTo(OnboardingStatus.DONE);
        assertThat(saved.styles()).containsExactlyInAnyOrder(TravelStyle.CULTURE, TravelStyle.FOOD, TravelStyle.NATURE);
        assertThat(saved.pace()).isEqualTo(Pace.PACKED);
        assertThat(saved.timeOfDay().startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.accessible()).isTrue();
    }

    private ResultActions save(String token, String body) throws Exception {
        return mockMvc.perform(json(put("/api/members/me/preferences"), token, body));
    }
}
