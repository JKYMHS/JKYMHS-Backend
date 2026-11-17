package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.auth.client.GoogleOAuthClient;
import com.jkymhs.backend.domain.auth.client.GoogleUserInfo;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserConsentRepository;
import com.jkymhs.backend.domain.user.repository.UserPreferenceRepository;
import com.jkymhs.backend.domain.user.repository.UserTravelStyleRepository;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.RefreshTokenCookies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class WithdrawApiTest extends MemberApiTestSupport {

    private static final String CODE = "4/0AX4XfWh-withdraw-code";
    private static final String REDIRECT_URI = "https://whereto.app/oauth/google";

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private UserTravelStyleRepository userTravelStyleRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("비밀번호가 맞으면 204와 삭제 쿠키를 주고, 회원·동의·취향·스타일·리프레시 토큰을 모두 지우고 탈퇴 이벤트를 발행한다")
    void withdrawWithPassword() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);
        tokenService.issue(user, "other device");
        mockMvc.perform(json(put("/api/members/me/preferences"), token, """
                        {"styleIds": ["rest", "food"], "pace": "relaxed", "timeOfDay": "morning", "accessible": false, "pet": true}
                        """))
                .andExpect(status().isOk());

        withdraw(token, """
                {"password": "%s"}
                """.formatted(PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(RefreshTokenCookies.cleared());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(userConsentRepository.count()).isZero();
        assertThat(userPreferenceRepository.count()).isZero();
        assertThat(userTravelStyleRepository.count()).isZero();
        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(events.stream(UserWithdrawnEvent.class))
                .singleElement()
                .extracting(UserWithdrawnEvent::userId).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("다른 회원의 데이터는 지우지 않는다")
    void otherUsersRemain() throws Exception {
        User other = userRepository.save(User.createWithPassword("other@example.com", "hash"));
        tokenService.issue(other, "other");
        User user = saveEmailUser();

        withdraw(accessToken(user), """
                {"password": "%s"}
                """.formatted(PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findAll()).singleElement().extracting(User::getId).isEqualTo(other.getId());
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("비밀번호가 틀리거나 없으면 401 IDENTITY_VERIFICATION_FAILED이고 아무것도 지우지 않는다")
    void wrongPassword() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);

        for (String body : new String[]{"{\"password\": \"wrong1234\"}", "{}"}) {
            withdraw(token, body)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("본인 확인에 실패했습니다."))
                    .andExpect(RefreshTokenCookies.absent());
        }
        // 본문 자체가 없어도 같은 401이다
        mockMvc.perform(auth(delete("/api/members/me"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(userConsentRepository.count()).isEqualTo(6);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(events.stream(UserWithdrawnEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("구글로만 가입한 회원은 구글 재인증으로 탈퇴한다")
    void withdrawWithGoogle() throws Exception {
        User user = saveGoogleOnlyUser();
        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willReturn(new GoogleUserInfo(GOOGLE_SUB, GOOGLE_EMAIL, true));

        withdraw(accessToken(user), googleBody())
                .andExpect(status().isNoContent())
                .andExpect(RefreshTokenCookies.cleared());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(events.stream(UserWithdrawnEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("구글 재인증: 다른 구글 계정이거나, 인가 코드가 거부되거나, 코드가 없으면 401")
    void googleVerificationFails() throws Exception {
        User user = saveGoogleOnlyUser();
        String token = accessToken(user);

        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willReturn(new GoogleUserInfo("another-sub", "another@gmail.com", true));
        withdraw(token, googleBody())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willThrow(new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "인가 코드 만료·재사용"));
        withdraw(token, googleBody())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

        // 비밀번호가 없는 회원이 비밀번호를 보내도 본인 확인이 안 된다
        withdraw(token, """
                {"password": "%s"}
                """.formatted(PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("구글 서버 장애는 본인 확인 실패가 아니라 502 GOOGLE_SERVER_ERROR다")
    void googleServerError() throws Exception {
        User user = saveGoogleOnlyUser();
        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willThrow(new CustomException(ErrorCode.GOOGLE_SERVER_ERROR));

        withdraw(accessToken(user), googleBody())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GOOGLE_SERVER_ERROR"));
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("탈퇴 요약: 숫자를 채우는 도메인이 아직 없으면 모두 0이다")
    void summary() throws Exception {
        mockMvc.perform(auth(get("/api/members/me/withdraw-summary"), accessToken(saveEmailUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripCount").value(0))
                .andExpect(jsonPath("$.planCount").value(0))
                .andExpect(jsonPath("$.wishCount").value(0))
                .andExpect(jsonPath("$.calendarEventCount").value(0));
    }

    private ResultActions withdraw(String token, String body) throws Exception {
        return mockMvc.perform(json(delete("/api/members/me"), token, body));
    }

    private static String googleBody() {
        return """
                {"googleCode": "%s", "redirectUri": "%s"}
                """.formatted(CODE, REDIRECT_URI);
    }
}
