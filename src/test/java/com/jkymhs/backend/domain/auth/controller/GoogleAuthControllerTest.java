package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.client.GoogleOAuthClient;
import com.jkymhs.backend.domain.auth.client.GoogleUserInfo;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.AuthRedisKeys;
import com.jkymhs.backend.domain.user.entity.OnboardingStatus;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구글 로그인·로그인 중 구글 계정 연결 API를 실제 DB·Redis로 확인한다.
 * 구글 서버만 {@code @MockitoBean}으로 바꿔서, 인가 코드를 넣으면 원하는 구글 계정 정보가 나오게 한다.
 */
class GoogleAuthControllerTest extends IntegrationTestSupport {

    private static final String CODE = "4/0AX4XfWh-test-code";
    private static final String REDIRECT_URI = "https://whereto.app/oauth/google";
    private static final String GOOGLE_SUB = "10769150350006150715";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "abc12345";

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("구글 로그인 POST /api/auth/google")
    class GoogleLogin {

        @Test
        @DisplayName("구글 계정이 연결된 회원이면 LOGGED_IN과 토큰을 준다")
        void loggedIn() throws Exception {
            userRepository.save(User.createWithGoogle(EMAIL, GOOGLE_SUB, EMAIL));
            givenGoogleAccount(GOOGLE_SUB, EMAIL);

            googleLogin()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("LOGGED_IN"))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"))
                    .andExpect(jsonPath("$.linkToken").doesNotExist())
                    .andExpect(jsonPath("$.email").doesNotExist());

            assertThat(refreshTokenRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("처음 보는 구글 계정이면 비밀번호 없는 계정을 만들고 SIGNED_UP과 토큰을 준다")
        void signedUp() throws Exception {
            givenGoogleAccount(GOOGLE_SUB, "new@gmail.com");

            googleLogin()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SIGNED_UP"))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"));

            User user = userRepository.findByGoogleSub(GOOGLE_SUB).orElseThrow();
            assertThat(user.getEmail()).isEqualTo("new@gmail.com");
            assertThat(user.getGoogleEmail()).isEqualTo("new@gmail.com");
            assertThat(user.hasPassword()).isFalse();
            assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
            assertThat(refreshTokenRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 이메일의 이메일 가입 계정이 있으면 LINK_REQUIRED와 linkToken만 주고, 연결 정보를 Redis에 10분 저장한다")
        void linkRequired() throws Exception {
            User user = saveEmailUser();
            // 구글 이메일 대소문자가 달라도 같은 이메일로 본다
            givenGoogleAccount(GOOGLE_SUB, "User@Example.com");

            String body = googleLogin()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("LINK_REQUIRED"))
                    .andExpect(jsonPath("$.linkToken").isNotEmpty())
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(jsonPath("$.onboardingStatus").doesNotExist())
                    .andReturn().getResponse().getContentAsString();

            String key = AuthRedisKeys.googleLink(JsonPath.read(body, "$.linkToken"));
            String value = redisTemplate.opsForValue().get(key);
            assertThat(((Number) JsonPath.read(value, "$.memberId")).longValue()).isEqualTo(user.getId());
            assertThat((String) JsonPath.read(value, "$.googleSub")).isEqualTo(GOOGLE_SUB);
            assertThat((String) JsonPath.read(value, "$.googleEmail")).isEqualTo(EMAIL);
            assertThat(redisTemplate.getExpire(key)).isBetween(590L, 600L);

            // 비밀번호 확인 전이라 아직 연결하지 않고 토큰도 발급하지 않는다
            assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleLinked()).isFalse();
            assertThat(refreshTokenRepository.count()).isZero();
        }

        @Test
        @DisplayName("같은 이메일 계정이 다른 구글 계정으로 가입한 구글 전용 계정이면 409")
        void sameEmailGoogleOnlyAccount() throws Exception {
            userRepository.save(User.createWithGoogle(EMAIL, "other-google-sub", EMAIL));
            givenGoogleAccount(GOOGLE_SUB, EMAIL);

            googleLogin()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
        }

        @Test
        @DisplayName("같은 이메일 계정에 이미 다른 구글 계정이 연결돼 있으면 덮어쓰지 않고 409")
        void sameEmailAccountLinkedToOtherGoogle() throws Exception {
            User user = saveEmailUser();
            user.linkGoogle("other-google-sub", "other@gmail.com");
            userRepository.save(user);
            givenGoogleAccount(GOOGLE_SUB, EMAIL);

            googleLogin()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
        }

        @Test
        @DisplayName("구글이 인가 코드를 거부하면 401")
        void googleAuthFailed() throws Exception {
            given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                    .willThrow(new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "인가 코드 만료·재사용"));

            googleLogin()
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("구글 인증에 실패했습니다. 다시 시도해주세요."))
                    .andExpect(jsonPath("$.description").value("인가 코드 만료·재사용"));

            assertThat(userRepository.count()).isZero();
        }

        @Test
        @DisplayName("구글 서버와 통신하지 못하면 502")
        void googleServerError() throws Exception {
            given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI))
                    .willThrow(new CustomException(ErrorCode.GOOGLE_SERVER_ERROR));

            googleLogin()
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").value("구글 서버와 통신하지 못했습니다."));
        }

        @Test
        @DisplayName("인가 코드가 비어 있으면 400")
        void blankCode() throws Exception {
            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"","redirectUri":"%s"}
                                    """.formatted(REDIRECT_URI)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("구글 인가 코드를 입력해주세요."));
        }
    }

    @Nested
    @DisplayName("로그인 중 구글 계정 연결 POST /api/auth/google/link")
    class GoogleLink {

        @Test
        @DisplayName("비밀번호가 맞으면 구글 계정을 연결하고 토큰을 준다. 이후 같은 구글 계정으로 로그인하면 LOGGED_IN")
        void linkSuccess() throws Exception {
            User user = saveEmailUser();
            String linkToken = requestLinkToken();
            // 앞선 실패 기록은 연결에 성공하면 지워진다
            link(linkToken, "wrong-password").andExpect(status().isUnauthorized());

            link(linkToken, PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"));

            User linked = userRepository.findById(user.getId()).orElseThrow();
            assertThat(linked.getGoogleSub()).isEqualTo(GOOGLE_SUB);
            assertThat(linked.getGoogleEmail()).isEqualTo(EMAIL);
            assertThat(linked.hasPassword()).isTrue();
            assertThat(redisTemplate.hasKey(AuthRedisKeys.googleLink(linkToken))).isFalse();
            assertThat(redisTemplate.hasKey(AuthRedisKeys.loginFail(EMAIL))).isFalse();
            assertThat(refreshTokenRepository.count()).isEqualTo(1);

            googleLogin()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("LOGGED_IN"));
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401이고, 이메일 로그인과 같은 실패 횟수(login:fail)에 합산된다")
        void wrongPassword() throws Exception {
            saveEmailUser();
            String linkToken = requestLinkToken();

            link(linkToken, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("비밀번호가 올바르지 않습니다."));

            assertThat(redisTemplate.opsForHash().get(AuthRedisKeys.loginFail(EMAIL), "failCount")).isEqualTo("1");
            // 다시 시도할 수 있도록 linkToken은 남겨둔다
            assertThat(redisTemplate.hasKey(AuthRedisKeys.googleLink(linkToken))).isTrue();
        }

        @Test
        @DisplayName("5번째로 틀리면 423이고, 그 뒤에는 맞는 비밀번호도 423")
        void lockedOnFifthFailure() throws Exception {
            saveEmailUser();
            String linkToken = requestLinkToken();

            for (int i = 0; i < 4; i++) {
                link(linkToken, "wrong-password").andExpect(status().isUnauthorized());
            }
            link(linkToken, "wrong-password")
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.message").value("로그인 시도가 많아 10분간 잠겼습니다."));

            link(linkToken, PASSWORD).andExpect(status().isLocked());
            assertThat(userRepository.findByGoogleSub(GOOGLE_SUB)).isEmpty();
        }

        @Test
        @DisplayName("linkToken이 만료됐거나 없는 값이면 401")
        void unknownLinkToken() throws Exception {
            saveEmailUser();

            link("unknown-link-token", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("비밀번호가 올바르지 않습니다."))
                    .andExpect(jsonPath("$.description").value("linkToken 만료"));
        }

        @Test
        @DisplayName("연결 대기 중에 회원이 없어졌으면 linkToken 만료와 같은 401")
        void memberGone() throws Exception {
            saveEmailUser();
            String linkToken = requestLinkToken();
            userRepository.deleteAll();

            link(linkToken, PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.description").value("linkToken 만료"));
        }

        @Test
        @DisplayName("연결 대기 중에 그 구글 계정이 다른 회원에게 연결됐으면 409")
        void googleAccountLinkedToOtherMember() throws Exception {
            User user = saveEmailUser();
            String linkToken = requestLinkToken();
            userRepository.save(User.createWithGoogle("other@example.com", GOOGLE_SUB, "other@example.com"));

            link(linkToken, PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("다른 계정에 이미 연결된 구글 계정입니다."));

            assertThat(userRepository.findById(user.getId()).orElseThrow().isGoogleLinked()).isFalse();
            assertThat(refreshTokenRepository.count()).isZero();
        }

        @Test
        @DisplayName("연결 대기 중에 이 회원에게 다른 구글 계정이 연결됐으면 덮어쓰지 않고 409")
        void memberLinkedToOtherGoogle() throws Exception {
            User user = saveEmailUser();
            String linkToken = requestLinkToken();
            user.linkGoogle("other-google-sub", "other@gmail.com");
            userRepository.save(user);

            link(linkToken, PASSWORD).andExpect(status().isConflict());

            assertThat(userRepository.findById(user.getId()).orElseThrow().getGoogleSub()).isEqualTo("other-google-sub");
        }
    }

    private User saveEmailUser() {
        return userRepository.save(User.createWithPassword(EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    private void givenGoogleAccount(String sub, String email) {
        given(googleOAuthClient.fetchUserInfo(CODE, REDIRECT_URI)).willReturn(new GoogleUserInfo(sub, email, true));
    }

    // 같은 이메일 계정이 있는 상태에서 구글 로그인을 해서 LINK_REQUIRED의 linkToken을 받는다
    private String requestLinkToken() throws Exception {
        givenGoogleAccount(GOOGLE_SUB, EMAIL);
        String body = googleLogin()
                .andExpect(jsonPath("$.result").value("LINK_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.linkToken");
    }

    private ResultActions googleLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","redirectUri":"%s"}
                        """.formatted(CODE, REDIRECT_URI)));
    }

    private ResultActions link(String linkToken, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/google/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"linkToken":"%s","password":"%s"}
                        """.formatted(linkToken, password)));
    }
}
