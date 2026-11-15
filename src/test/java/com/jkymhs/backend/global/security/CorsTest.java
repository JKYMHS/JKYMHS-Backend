package com.jkymhs.backend.global.security;

import com.jkymhs.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 브라우저가 보내는 요청 그대로 Origin 헤더를 붙여서 CORS 응답을 확인한다.
 * 쉼표 뒤 공백도 정리되는지 보려고 일부러 공백을 넣었다.
 */
@WebMvcTest(value = CorsTest.TestController.class,
        properties = "cors.allowed-origins=https://app.example.com, http://localhost:3000")
@Import({SecurityConfig.class, JwtProvider.class, CorsTest.TestController.class})
@ActiveProfiles("test")
class CorsTest {

    private static final String ALLOWED_ORIGIN = "https://app.example.com";
    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/test/me", "/api/auth/reissue"})
    @DisplayName("허용된 Origin의 사전 요청은 인증 없이 200이고 쿠키 사용을 허용한다 (인증 필요한 경로도 401이 아니다)")
    void preflightFromAllowedOrigin(String path) throws Exception {
        preflight(path, ALLOWED_ORIGIN, "POST")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    @DisplayName("쉼표로 나열한 Origin은 모두 허용한다")
    void secondAllowedOrigin() throws Exception {
        preflight("/api/test/me", "http://localhost:3000", "GET")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/test/me", "/api/auth/reissue"})
    @DisplayName("허용되지 않은 Origin의 사전 요청은 403이고 CORS 허용 헤더가 없다")
    void preflightFromDisallowedOrigin(String path) throws Exception {
        preflight(path, DISALLOWED_ORIGIN, "POST")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("허용되지 않은 Origin은 사전 요청 없이 바로 보내도 컨트롤러까지 가지 않고 403이다")
    void actualRequestFromDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/reissue").header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("허용하지 않은 헤더를 쓰겠다는 사전 요청은 403이다")
    void preflightWithDisallowedHeader() throws Exception {
        mockMvc.perform(options("/api/test/me")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom"))
                .andExpect(status().isForbidden());
    }

    // 이 헤더가 없으면 브라우저가 401 응답을 FE 코드에 보여주지 않아서, FE가 재발급을 시도할 수 없다
    @Test
    @DisplayName("허용된 Origin의 요청이 401로 끝나도 응답에 CORS 허용 헤더가 있다")
    void errorResponseKeepsCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    private ResultActions preflight(String path, String origin, String method) throws Exception {
        return mockMvc.perform(options(path)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/me")
        void me() {
        }
    }
}
