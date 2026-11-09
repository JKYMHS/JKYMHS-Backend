package com.jkymhs.backend.global.web;

import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodeEnumConverterTest.TestController.class)
// 인증 브랜치와 합쳐지면 스프링 시큐리티가 테스트 컨트롤러까지 막는다(401). 여기서는 에러 응답·변환만 보므로 보안 필터를 끈다
@AutoConfigureMockMvc(addFilters = false)
@Import(CodeEnumConverterTest.TestController.class)
class CodeEnumConverterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("쿼리 파라미터의 FE 코드(소문자)를 enum 상수로 바꾸고, 응답 JSON에는 다시 코드로 쓴다")
    void code() throws Exception {
        mockMvc.perform(get("/test/enum").param("companion", "kids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("KIDS"))
                .andExpect(jsonPath("$.json").value("kids"))
                .andExpect(jsonPath("$.slowWalker").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FRIENDS", "Friends", " friends "})
    @DisplayName("코드와 정확히 같지 않아도 상수 이름과 대소문자 무시로 같으면 받는다")
    void name(String value) throws Exception {
        mockMvc.perform(get("/test/enum").param("companion", value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("FRIENDS"))
                .andExpect(jsonPath("$.slowWalker").value(false));
    }

    @Test
    @DisplayName("code와 name이 같은 대문자 enum도 같은 방식으로 바뀐다")
    void upperCaseEnum() throws Exception {
        mockMvc.perform(get("/test/age").param("ageGroup", "ELEMENTARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value("ELEMENTARY"));
    }

    @Test
    @DisplayName("@RequestParam에 없는 값이 오면 400 INVALID_INPUT이다")
    void unknownRequestParam() throws Exception {
        mockMvc.perform(get("/test/enum").param("companion", "dog"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.description").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("@ModelAttribute 요청 record도 코드로 바인딩된다")
    void modelAttribute() throws Exception {
        mockMvc.perform(get("/test/search").param("companion", "parents").param("ageGroup", "teen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companion").value("parents"))
                .andExpect(jsonPath("$.ageGroup").value("TEEN"));
    }

    @Test
    @DisplayName("@ModelAttribute에 없는 값이 오면 스프링 영어 문구 대신 @RequestParam 타입 오류와 같은 문구로 400이다")
    void unknownModelAttribute() throws Exception {
        mockMvc.perform(get("/test/search").param("companion", "dog"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.description").value("companion: 요청 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("@ModelAttribute에 값이 비어 있으면 null로 바인딩되어 @NotNull 메시지가 나온다")
    void emptyModelAttribute() throws Exception {
        mockMvc.perform(get("/test/search").param("companion", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("동행을 선택해주세요."));
    }

    @Test
    @DisplayName("JSON 본문의 enum도 코드로 읽고, 없는 코드면 400이다")
    void requestBody() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companion": "couple"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companion").value("couple"));

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companion": "dog"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.description").value("요청 형식이 올바르지 않습니다."));
    }

    record SearchRequest(@NotNull(message = "동행을 선택해주세요.") Companion companion, ChildAgeGroup ageGroup) {
    }

    record BodyRequest(Companion companion) {
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/enum")
        Map<String, Object> enumParam(@RequestParam Companion companion) {
            return Map.of("name", companion.name(), "json", companion, "slowWalker", companion.slowWalker());
        }

        @GetMapping("/age")
        Map<String, Object> ageParam(@RequestParam ChildAgeGroup ageGroup) {
            return Map.of("json", ageGroup);
        }

        @GetMapping("/search")
        SearchRequest search(@Valid @ModelAttribute SearchRequest request) {
            return request;
        }

        @PostMapping("/body")
        BodyRequest body(@RequestBody BodyRequest request) {
            return request;
        }
    }
}
