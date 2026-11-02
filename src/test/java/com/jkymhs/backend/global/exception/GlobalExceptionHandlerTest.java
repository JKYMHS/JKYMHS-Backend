package com.jkymhs.backend.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false) // 에러 응답 형식만 보는 테스트라서 시큐리티 필터는 끈다 (보안은 SecurityConfigTest에서 확인)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CustomException은 ErrorCode의 상태코드와 메시지로 응답한다")
    void customException() throws Exception {
        mockMvc.perform(get("/test/custom"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.description").value("잠시 후 다시 시도해주세요."));
    }

    @Test
    @DisplayName("CustomException에 설명을 넘기면 description에 담긴다")
    void customExceptionWithDescription() throws Exception {
        mockMvc.perform(get("/test/custom-description"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.description").value("남은 시도 횟수: 3"));
    }

    @Test
    @DisplayName("@Valid 검증에 실패하면 400과 필드 메시지로 응답한다")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "abc", "password": "abc12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 이메일 형식이 아닙니다."))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.description").value("email: 올바른 이메일 형식이 아닙니다."));
    }

    @Test
    @DisplayName("JSON 형식이 깨지면 400으로 응답한다")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.description").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("파라미터 타입이 맞지 않으면 400으로 응답한다")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/param").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 400으로 응답한다")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("없는 경로는 404로 응답한다")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405와 허용 메서드(Allow 헤더)로 응답한다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/custom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.description").value("DELETE 메서드는 지원하지 않습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 500이 아니라 415로 응답한다")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("파라미터 검증(@Min)에 실패하면 500이 아니라 400으로 응답한다")
    void parameterValidationFailure() throws Exception {
        mockMvc.perform(get("/test/positive").param("value", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Redis 연결에 실패하면 503으로 응답한다")
    void redisFailure() throws Exception {
        mockMvc.perform(get("/test/redis"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.description").value("일시적인 서버 장애"));
    }

    @Test
    @DisplayName("Redis 명령 도중 오류(RedisSystemException)도 500이 아니라 503으로 응답한다")
    void redisSystemFailure() throws Exception {
        mockMvc.perform(get("/test/redis-system"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("클라이언트가 JSON을 요청하지 않아도 에러 응답은 JSON으로 내려준다")
    void errorBodyIsJsonRegardlessOfAccept() throws Exception {
        mockMvc.perform(get("/test/redis").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500으로 응답하고 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.description").value("서버 내부 오류"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record TestRequest(
                @NotBlank @Email(message = "올바른 이메일 형식이 아닙니다.") String email,
                @NotBlank(message = "비밀번호를 입력해주세요.") String password
        ) {
        }

        @GetMapping("/custom")
        void custom() {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        @GetMapping("/custom-description")
        void customWithDescription() {
            throw new CustomException(ErrorCode.INVALID_INPUT, "남은 시도 횟수: 3");
        }

        @PostMapping("/valid")
        void valid(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/param")
        void param(@RequestParam Long id) {
        }

        @GetMapping("/positive")
        void positive(@RequestParam @Min(1) int value) {
        }

        @GetMapping("/redis")
        void redis() {
            throw new RedisConnectionFailureException("Unable to connect to Redis");
        }

        @GetMapping("/redis-system")
        void redisSystem() {
            throw new RedisSystemException("LOADING Redis is loading the dataset in memory", null);
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret: db password");
        }
    }
}
