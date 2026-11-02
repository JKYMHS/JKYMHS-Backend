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

import java.net.Socket;
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

    // 안 는 UTF-8로 EC 95 88이다. 서버는 헤더를 ISO-8859-1로 읽으므로 0x95, 0x88이 제어 문자가 돼서 방화벽이 거절한다
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/auth/reissue", "/api/auth/login"})
    @DisplayName("User-Agent에 한글(UTF-8)이 그대로 오면 500이 아니라 400 공통 에러 형식으로 응답한다")
    void nonLatin1UserAgent(String path) throws Exception {
        String body = "{\"email\":\"a@example.com\",\"password\":\"abc12345\"}";
        String response = sendRaw("POST " + path + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "User-Agent: MyApp \u00EC\u0095\u0088\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "Connection: close\r\n\r\n"
                + body);

        assertThat(response).startsWith("HTTP/1.1 400");
        String json = response.lines().filter(line -> line.startsWith("{")).findFirst().orElseThrow();
        assertThat((String) JsonPath.read(json, "$.code")).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("실제 서버에서 /error를 직접 호출하면 404다")
    void directCallOnRealServer() throws Exception {
        HttpResponse<String> response = send("/error");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "400, INVALID_INPUT",
            "401, UNAUTHORIZED",
            "403, FORBIDDEN",
            "404, RESOURCE_NOT_FOUND",
            "405, METHOD_NOT_ALLOWED",
            "413, INVALID_INPUT",
            "429, INVALID_INPUT",
            "500, INTERNAL_SERVER_ERROR",
            "502, INTERNAL_SERVER_ERROR",
            "503, SERVICE_UNAVAILABLE"
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
    @DisplayName("상태코드 정보 없이 /error를 직접 호출하면 에러가 아니므로 404로 응답한다")
    void directCall() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private HttpResponse<String> send(String path) throws Exception {
        // URI.create는 //와 ;를 그대로 보낸다. 브라우저처럼 경로를 정리하면 방화벽까지 가지 않는다
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    // HttpClient는 잘못된 헤더 값을 보내주지 않아서 소켓에 바이트를 직접 쓴다
    private String sendRaw(String rawRequest) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(rawRequest.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
