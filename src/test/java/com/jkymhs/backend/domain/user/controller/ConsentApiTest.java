package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.entity.UserConsent;
import com.jkymhs.backend.domain.user.repository.UserConsentRepository;
import com.jkymhs.backend.global.event.BehaviorConsentRevokedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 발행된 이벤트는 {@code @RecordApplicationEvents}로 기록해서 확인한다 (빈을 추가하지 않아서 테스트 컨텍스트를 새로 띄우지 않는다).
 */
@RecordApplicationEvents
class ConsentApiTest extends MemberApiTestSupport {

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("동의 조회: 6개 항목을 필수 3개 → 위치 → 행동 데이터 → 이메일 알림 순서로 준다")
    void getConsents() throws Exception {
        User user = saveEmailUser();

        mockMvc.perform(auth(get("/api/members/me/consents"), accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[*].type").value(contains(
                        "AGE_14", "TERMS", "PRIVACY", "LOCATION", "BEHAVIOR_DATA", "EMAIL_NOTIFICATION")))
                .andExpect(jsonPath("$.consents[*].required").value(contains(true, true, true, false, false, false)))
                .andExpect(jsonPath("$.consents[*].agreed").value(contains(true, true, true, false, true, false)))
                .andExpect(jsonPath("$.consents[0].agreedAt").isNotEmpty())
                .andExpect(jsonPath("$.consents[3].agreedAt").value(nullValue()));
    }

    @Test
    @DisplayName("동의 조회: 동의 행이 없는 구글 가입 회원은 모두 미동의로 준다")
    void getConsentsWithoutRows() throws Exception {
        User user = saveGoogleOnlyUser();

        mockMvc.perform(auth(get("/api/members/me/consents"), accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents.length()").value(6))
                .andExpect(jsonPath("$.consents[*].agreed").value(contains(false, false, false, false, false, false)));
    }

    @Test
    @DisplayName("위치 동의를 켜고 끄면 바꾼 항목만 돌려주고, 켤 때 agreedAt을 기록하고 끌 때 지운다")
    void toggleLocation() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);

        update(token, """
                {"consents": [{"type": "LOCATION", "agreed": true}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents.length()").value(1))
                .andExpect(jsonPath("$.consents[0].type").value("LOCATION"))
                .andExpect(jsonPath("$.consents[0].required").value(false))
                .andExpect(jsonPath("$.consents[0].agreed").value(true))
                .andExpect(jsonPath("$.consents[0].agreedAt").isNotEmpty());
        assertThat(userConsentService.isAgreed(user.getId(), ConsentType.LOCATION)).isTrue();

        update(token, """
                {"consents": [{"type": "LOCATION", "agreed": false}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].agreed").value(false))
                .andExpect(jsonPath("$.consents[0].agreedAt").value(nullValue()));
        assertThat(userConsentService.isAgreed(user.getId(), ConsentType.LOCATION)).isFalse();
    }

    @Test
    @DisplayName("이미 동의한 항목에 다시 동의하면 처음 동의한 시각을 그대로 둔다")
    void agreeAgainKeepsAgreedAt() throws Exception {
        User user = saveEmailUser();
        LocalDateTime before = consent(user, ConsentType.EMAIL_NOTIFICATION).getAgreedAt();
        assertThat(before).isNull();
        String token = accessToken(user);
        update(token, """
                {"consents": [{"type": "EMAIL_NOTIFICATION", "agreed": true}]}
                """).andExpect(status().isOk());
        LocalDateTime first = consent(user, ConsentType.EMAIL_NOTIFICATION).getAgreedAt();

        update(token, """
                {"consents": [{"type": "EMAIL_NOTIFICATION", "agreed": true}]}
                """).andExpect(status().isOk());

        assertThat(consent(user, ConsentType.EMAIL_NOTIFICATION).getAgreedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("필수 항목을 끄면 400 REQUIRED_CONSENT_CANNOT_REVOKE이고, 같이 보낸 다른 항목도 바꾸지 않는다")
    void revokeRequired() throws Exception {
        User user = saveEmailUser();

        update(accessToken(user), """
                {"consents": [{"type": "LOCATION", "agreed": true}, {"type": "TERMS", "agreed": false}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_CANNOT_REVOKE"))
                .andExpect(jsonPath("$.message").value("필수 약관 동의는 철회할 수 없습니다. 회원 탈퇴를 이용해주세요."));

        assertThat(consent(user, ConsentType.TERMS).isAgreed()).isTrue();
        assertThat(consent(user, ConsentType.LOCATION).isAgreed()).isFalse();
    }

    @Test
    @DisplayName("구글 가입 회원이 필수 3개를 true로 보내면 행을 새로 만들어 저장한다")
    void agreeRequiredForGoogleUser() throws Exception {
        User user = saveGoogleOnlyUser();

        update(accessToken(user), """
                {"consents": [{"type": "PRIVACY", "agreed": true}, {"type": "AGE_14", "agreed": true},
                              {"type": "TERMS", "agreed": true}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[*].type").value(contains("AGE_14", "TERMS", "PRIVACY")))
                .andExpect(jsonPath("$.consents[*].agreed").value(contains(true, true, true)));

        assertThat(userConsentRepository.findAllByUserId(user.getId())).hasSize(3).allMatch(UserConsent::isAgreed);
    }

    @Test
    @DisplayName("행동 데이터 동의를 끄면 같은 요청 안에서 BehaviorConsentRevokedEvent를 발행한다")
    void revokeBehaviorPublishesEvent() throws Exception {
        User user = saveEmailUser();

        update(accessToken(user), """
                {"consents": [{"type": "BEHAVIOR_DATA", "agreed": false}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].agreed").value(false));

        assertThat(events.stream(BehaviorConsentRevokedEvent.class))
                .singleElement()
                .extracting(BehaviorConsentRevokedEvent::userId).isEqualTo(user.getId());
        assertThat(userConsentService.isAgreed(user.getId(), ConsentType.BEHAVIOR_DATA)).isFalse();
    }

    @Test
    @DisplayName("행동 데이터 동의를 켜거나 다른 항목만 바꾸면 이벤트를 발행하지 않는다")
    void otherChangesDoNotPublish() throws Exception {
        String token = accessToken(saveEmailUser());

        update(token, """
                {"consents": [{"type": "BEHAVIOR_DATA", "agreed": true}, {"type": "LOCATION", "agreed": false}]}
                """).andExpect(status().isOk());

        assertThat(events.stream(BehaviorConsentRevokedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("빈 목록·같은 항목 두 번·모르는 항목·agreed 누락은 400")
    void invalidRequests() throws Exception {
        String token = accessToken(saveEmailUser());

        update(token, """
                {"consents": []}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("변경할 동의 항목을 입력해주세요."));
        update(token, """
                {"consents": [{"type": "LOCATION", "agreed": true}, {"type": "LOCATION", "agreed": false}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("같은 동의 항목을 두 번 보낼 수 없습니다."));
        update(token, """
                {"consents": [{"type": "MARKETING", "agreed": true}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
        update(token, """
                {"consents": [{"type": "LOCATION"}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("동의 여부를 입력해주세요."));
        assertThat(events.stream(BehaviorConsentRevokedEvent.class)).isEmpty();
    }

    private ResultActions update(String token, String body) throws Exception {
        return mockMvc.perform(json(put("/api/members/me/consents"), token, body));
    }

    private UserConsent consent(User user, ConsentType type) {
        return userConsentRepository.findAllByUserId(user.getId()).stream()
                .filter(consent -> consent.getConsentType() == type)
                .findFirst().orElseThrow();
    }
}
