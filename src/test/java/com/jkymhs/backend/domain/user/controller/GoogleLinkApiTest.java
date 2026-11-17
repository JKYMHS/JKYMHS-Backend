package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.auth.client.GoogleOAuthClient;
import com.jkymhs.backend.domain.auth.client.GoogleUserInfo;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구글 서버는 auth 테스트(GoogleAuthControllerTest)와 같은 방식으로 GoogleOAuthClient를 {@code @MockitoBean}으로 바꾼다.
 * 바꾸는 빈이 같아서 그 테스트와 스프링 컨텍스트를 같이 쓴다.
 */
class GoogleLinkApiTest extends MemberApiTestSupport {

    private static final String CODE = "4/0AX4XfWh-test-code";
    private static final String REDIRECT_URI = "https://whereto.app/oauth/google";
    private static final String LINK_BODY = """
            {"code": "%s", "redirectUri": "%s"}
            """.formatted(CODE, REDIRECT_URI);

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @Test
    @DisplayName("구글 연결: 로그인 이메일과 다른 구글 계정도 연결되고 연결 상태를 준다")
    void link() throws Exception {
        User user = saveEmailUser();
        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willReturn(new GoogleUserInfo(GOOGLE_SUB, GOOGLE_EMAIL, true));

        link(accessToken(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google.linked").value(true))
                .andExpect(jsonPath("$.google.email").value(GOOGLE_EMAIL));

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getGoogleSub()).isEqualTo(GOOGLE_SUB);
        assertThat(saved.getGoogleEmail()).isEqualTo(GOOGLE_EMAIL);
    }

    @Test
    @DisplayName("구글 연결: 그 구글 계정이 다른 회원에게 연결돼 있으면 409 GOOGLE_ACCOUNT_ALREADY_LINKED")
    void linkedToOtherUser() throws Exception {
        saveGoogleOnlyUser();
        User user = saveEmailUser();
        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willReturn(new GoogleUserInfo(GOOGLE_SUB, GOOGLE_EMAIL, true));

        link(accessToken(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_ALREADY_LINKED"))
                .andExpect(jsonPath("$.message").value("다른 계정에 이미 연결된 구글 계정입니다."));
        assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleLinked()).isFalse();
    }

    @Test
    @DisplayName("구글 연결: 이미 구글이 연결된 회원이면 구글을 부르지 않고 409")
    void alreadyLinkedToMe() throws Exception {
        User user = saveGoogleOnlyUser();

        link(accessToken(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_ALREADY_LINKED"));
        then(googleOAuthClient).should(never()).fetchUserInfo(any(), any());
    }

    @Test
    @DisplayName("구글 연결: 인가 코드가 거부되면 401 GOOGLE_AUTH_FAILED, 구글 서버 장애면 502 GOOGLE_SERVER_ERROR")
    void googleFailures() throws Exception {
        String token = accessToken(saveEmailUser());

        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willThrow(new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "인가 코드 만료·재사용"));
        link(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("구글 인증에 실패했습니다. 다시 시도해주세요."));

        // 위에서 예외를 던지게 해둔 메서드라 given(mock.메서드())로 다시 정하면 그 호출에서 예외가 난다. willThrow().given() 형태로 바꾼다
        willThrow(new CustomException(ErrorCode.GOOGLE_SERVER_ERROR))
                .given(googleOAuthClient).fetchUserInfo(CODE, REDIRECT_URI);
        link(token)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GOOGLE_SERVER_ERROR"));
    }

    @Test
    @DisplayName("구글 연결: code가 없으면 400")
    void missingCode() throws Exception {
        mockMvc.perform(json(post("/api/members/me/google"), accessToken(saveEmailUser()), """
                        {"redirectUri": "%s"}
                        """.formatted(REDIRECT_URI)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("구글 인가 코드를 입력해주세요."));
    }

    @Test
    @DisplayName("연결한 뒤 해제하면 204이고 구글 sub·이메일이 지워진다")
    void linkThenUnlink() throws Exception {
        User user = saveEmailUser();
        String token = accessToken(user);
        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                .willReturn(new GoogleUserInfo(GOOGLE_SUB, GOOGLE_EMAIL, true));
        link(token).andExpect(status().isOk());

        mockMvc.perform(auth(delete("/api/members/me/google"), token))
                .andExpect(status().isNoContent());

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getGoogleSub()).isNull();
        assertThat(saved.getGoogleEmail()).isNull();
    }

    private ResultActions link(String token) throws Exception {
        return mockMvc.perform(json(post("/api/members/me/google"), token, LINK_BODY));
    }
}
