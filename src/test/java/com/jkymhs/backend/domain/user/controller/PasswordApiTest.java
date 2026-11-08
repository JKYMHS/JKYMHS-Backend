package com.jkymhs.backend.domain.user.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.support.RefreshTokenCookies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordApiTest extends MemberApiTestSupport {

    private static final String NEW_PASSWORD = "newpass99";

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Nested
    @DisplayName("비밀번호 변경 PATCH /api/members/me/password")
    class Change {

        @Test
        @DisplayName("바꾸면 모든 기기의 리프레시 토큰을 지우고, 이 기기에 새 액세스 토큰(본문)과 리프레시 토큰(쿠키)을 준다")
        void change() throws Exception {
            User user = saveEmailUser();
            IssuedTokens thisDevice = tokenService.issue(user, "this device");
            IssuedTokens otherDevice = tokenService.issue(user, "other device");

            MvcResult result = change(thisDevice.accessToken(), PASSWORD, NEW_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(RefreshTokenCookies.issued())
                    .andReturn();

            // 새 비밀번호가 저장됐다
            User saved = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPasswordHash())).isTrue();
            // 남은 토큰은 방금 발급한 하나뿐이고, 이 요청의 User-Agent로 기록된다
            assertThat(refreshTokenRepository.findAll()).singleElement()
                    .extracting(RefreshToken::getUserAgent).isEqualTo("password-test-agent");
            // 다른 기기의 리프레시 토큰으로는 더 이상 재발급할 수 없고, 새 토큰으로는 된다
            reissue(otherDevice.refreshToken()).andExpect(status().isUnauthorized());
            reissue(RefreshTokenCookies.tokenOf(result)).andExpect(status().isOk());
            // 새 액세스 토큰으로 API를 부를 수 있다
            String newAccessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
            mockMvc.perform(auth(get("/api/members/me"), newAccessToken)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 401 INVALID_CURRENT_PASSWORD이고 토큰을 지우지 않는다")
        void wrongCurrent() throws Exception {
            User user = saveEmailUser();

            change(accessToken(user), "wrong1234", NEW_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
                    .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않습니다."));

            assertThat(refreshTokenRepository.count()).isEqualTo(1);
            assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(user.getId()).orElseThrow()
                    .getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 400")
        void sameAsCurrent() throws Exception {
            change(accessToken(saveEmailUser()), PASSWORD, PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("현재 비밀번호와 다른 비밀번호를 입력해주세요."));
        }

        @Test
        @DisplayName("새 비밀번호가 규칙(영문+숫자 8~20자)에 맞지 않으면 400")
        void invalidFormat() throws Exception {
            String token = accessToken(saveEmailUser());

            for (String password : new String[]{"onlyletters", "12345678", "ab1", "abc 12345"}) {
                change(token, PASSWORD, password)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                        .andExpect(jsonPath("$.message").value("비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요."));
            }
        }

        @Test
        @DisplayName("구글로만 가입한 회원은 409 GOOGLE_ONLY_ACCOUNT와 비밀번호 설정 안내 문구")
        void googleOnly() throws Exception {
            change(accessToken(saveGoogleOnlyUser()), PASSWORD, NEW_PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GOOGLE_ONLY_ACCOUNT"))
                    .andExpect(jsonPath("$.message").value("비밀번호가 없는 계정입니다. 비밀번호 설정을 이용해주세요."));
        }

        private ResultActions change(String token, String current, String next) throws Exception {
            return mockMvc.perform(json(patch("/api/members/me/password"), token, """
                            {"currentPassword": "%s", "newPassword": "%s"}
                            """.formatted(current, next))
                    .header(HttpHeaders.USER_AGENT, "password-test-agent"));
        }

        private ResultActions reissue(String refreshToken) throws Exception {
            return mockMvc.perform(post("/api/auth/reissue").cookie(RefreshTokenCookies.cookie(refreshToken)));
        }
    }

    @Nested
    @DisplayName("비밀번호 설정 POST /api/members/me/password")
    class SetPassword {

        @Test
        @DisplayName("구글로만 가입한 회원이 비밀번호를 정하면 204이고, 이후 이메일·비밀번호로 로그인할 수 있다")
        void set() throws Exception {
            User user = saveGoogleOnlyUser();

            set(accessToken(user), NEW_PASSWORD)
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            assertThat(userRepository.findById(user.getId()).orElseThrow().hasPassword()).isTrue();
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email": "%s", "password": "%s"}
                            """.formatted(GOOGLE_EMAIL, NEW_PASSWORD)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("이미 비밀번호가 있으면 409 PASSWORD_ALREADY_SET")
        void alreadySet() throws Exception {
            set(accessToken(saveEmailUser()), NEW_PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PASSWORD_ALREADY_SET"))
                    .andExpect(jsonPath("$.message").value("이미 비밀번호가 설정된 계정입니다."));
        }

        @Test
        @DisplayName("규칙에 맞지 않으면 400")
        void invalidFormat() throws Exception {
            set(accessToken(saveGoogleOnlyUser()), "short1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요."));
        }

        private ResultActions set(String token, String password) throws Exception {
            return mockMvc.perform(json(post("/api/members/me/password"), token, """
                    {"newPassword": "%s"}
                    """.formatted(password)));
        }
    }
}
