package com.jkymhs.backend.global.security;

import com.jkymhs.backend.global.security.jwt.JwtProperties;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityConfig를 올려서 어떤 경로가 열려 있고, 토큰 상태별로 어떤 응답이 나가는지 확인한다.
 * DB가 필요 없어서 @WebMvcTest로 웹 계층만 띄운다.
 */
@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, JwtProvider.class, SecurityConfigTest.TestController.class})
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("유효한 토큰이면 인증된 회원 ID가 컨트롤러로 전달된다")
    void validToken() throws Exception {
        String token = jwtProvider.createAccessToken(7L);

        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    @DisplayName("비동기로 응답하는 API(Callable 등)도 두 번째 디스패치에서 인증이 유지된다")
    void asyncDispatchKeepsAuthentication() throws Exception {
        String token = jwtProvider.createAccessToken(7L);

        var started = mockMvc.perform(get("/api/test/async").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    @DisplayName("토큰 없이 인증이 필요한 API를 호출하면 401 공통 에러 형식으로 응답한다")
    void noToken() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.description").value("액세스 토큰 없음"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("만료된 토큰은 401로 응답하고 description으로 만료를 알려준다")
    void expiredToken() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(
                jwtProperties.secret(), Duration.ofSeconds(-1), jwtProperties.refreshTokenExpiration()));
        String token = expiredProvider.createAccessToken(7L);

        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.description").value("만료된 액세스 토큰"));
    }

    @Test
    @DisplayName("위조되거나 형식이 깨진 토큰은 401로 응답하고 description으로 사유를 알려준다")
    void invalidToken() throws Exception {
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.description").value("유효하지 않은 액세스 토큰"));
    }

    @Test
    @DisplayName("/api/auth/** 는 토큰 없이 호출할 수 있다")
    void authPathIsOpen() throws Exception {
        mockMvc.perform(post("/api/auth/open"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/auth/** 는 잘못된 토큰이 붙어 있어도 막지 않는다 (만료 토큰을 든 채 로그인하는 경우)")
    void authPathIgnoresBadToken() throws Exception {
        mockMvc.perform(post("/api/auth/open").header(HttpHeaders.AUTHORIZATION, "Bearer broken"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃은 /api/auth/** 지만 토큰이 필요하다")
    void logoutNeedsToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("열린 경로에서 난 에러는 401로 바뀌지 않고 원래 상태코드로 응답한다")
    void errorOnOpenPathKeepsStatus() throws Exception {
        mockMvc.perform(get("/api/auth/not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/me")
        Long me(@AuthenticationPrincipal Long userId) {
            return userId;
        }

        @GetMapping("/api/test/async")
        Callable<Long> async(@AuthenticationPrincipal Long userId) {
            return () -> userId;
        }

        @PostMapping("/api/auth/open")
        void open() {
        }

        @PostMapping("/api/auth/logout")
        void logout() {
        }
    }
}
