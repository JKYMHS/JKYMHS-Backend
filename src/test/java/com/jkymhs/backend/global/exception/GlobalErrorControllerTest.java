package com.jkymhs.backend.global.exception;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.support.IntegrationTestSupport;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러까지 오지 못한 에러의 응답 형식을 확인한다.
 * MockMvc는 톰캣 없이 동작해서 /error로 다시 보내주지 않는다. 그래서 방화벽 거절은 실제 서버(RANDOM_PORT)에 HTTP로 요청을 보내 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalErrorControllerTest extends IntegrationTestSupport {

    @LocalServerPort
    private int port;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api//x", "/api/x;y=1"})
    @DisplayName("시큐리티 방화벽이 거절한 URL도 공통 에러 형식으로 응답한다")
    void firewallRejection(String path) throws Exception {
        HttpResponse<String> response = send(path);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/json"));
        String body = response.body();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("INVALID_INPUT");
        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(400);
        assertThat((String) JsonPath.read(body, "$.timestamp")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.description")).isNotBlank();
        // 부트 기본 응답의 필드가 나오면 안 된다
        assertThat(body).doesNotContain("\"error\"", "\"path\"");
    }

    @Test
    @DisplayName("실제 서버에서 인증 없이 호출해도 401 공통 에러 형식이다")
    void unauthorizedOnRealServer() throws Exception {
        HttpResponse<String> response = send("/api/members/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UNAUTHORIZED");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "400, INVALID_INPUT",
            "401, UNAUTHORIZED",
            "403, FORBIDDEN",
            "404, RESOURCE_NOT_FOUND",
            "405, METHOD_NOT_ALLOWED",
            "413, INTERNAL_SERVER_ERROR",
            "500, INTERNAL_SERVER_ERROR"
    })
    @DisplayName("/error로 넘어온 상태코드를 유지하고 그에 맞는 code로 응답한다 (sendError로 끝난 경우 포함)")
    void statusToCode(int status, String code) throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "secret: internal message"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value(status))
                // 톰캣이 넘겨준 내부 메시지는 응답에 나가지 않는다
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @DisplayName("상태코드 정보 없이 /error를 직접 호출하면 500으로 응답한다")
    void directCall() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private HttpResponse<String> send(String path) throws Exception {
        // URI.create는 //와 ;를 그대로 보낸다. 브라우저처럼 경로를 정리하면 방화벽까지 가지 않는다
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}
