package com.jkymhs.backend.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomExceptionMessageTest.TestController.class)
// 인증 브랜치와 합쳐지면 스프링 시큐리티가 테스트 컨트롤러까지 막는다(401). 여기서는 에러 응답·변환만 보므로 보안 필터를 끈다
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionMessageTest.TestController.class)
class CustomExceptionMessageTest {

    private static final String PER_DAY_MESSAGE = "하루에는 장소를 최대 10개까지 넣을 수 있습니다.";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("문구를 지정해서 던지면 상태코드는 ErrorCode 그대로이고 message만 바뀐다")
    void overriddenMessage() throws Exception {
        mockMvc.perform(get("/test/override"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(PER_DAY_MESSAGE))
                .andExpect(jsonPath("$.description").value("하루 장소 수 초과"));
    }

    @Test
    @DisplayName("문구를 지정하지 않으면 ErrorCode의 기본 문구가 나간다")
    void defaultMessage() throws Exception {
        mockMvc.perform(get("/test/default"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("일정에는 장소를 최대 30개까지 넣을 수 있습니다."))
                .andExpect(jsonPath("$.description").value("전체 장소 수 초과"));
    }

    @Test
    @DisplayName("422 에러 코드는 422로 응답한다")
    void unprocessable() throws Exception {
        mockMvc.perform(get("/test/unprocessable"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").value("일정안 기간이 여행 기간보다 깁니다."));
    }

    @Test
    @DisplayName("예외의 getMessage()도 지정한 문구다 (로그에 실제로 보여준 문구가 남는다)")
    void exceptionMessage() {
        CustomException overridden = new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, PER_DAY_MESSAGE, "설명");
        CustomException defaults = new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED);

        assertThat(overridden.getMessage()).isEqualTo(PER_DAY_MESSAGE);
        assertThat(overridden.getErrorCode()).isEqualTo(ErrorCode.SCHEDULE_LIMIT_EXCEEDED);
        assertThat(defaults.getMessage()).isEqualTo(ErrorCode.SCHEDULE_LIMIT_EXCEEDED.getMessage());
        assertThat(defaults.getDescription()).isEqualTo(ErrorCode.SCHEDULE_LIMIT_EXCEEDED.getMessage());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/override")
        void override() {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, PER_DAY_MESSAGE, "하루 장소 수 초과");
        }

        @GetMapping("/default")
        void defaults() {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, "전체 장소 수 초과");
        }

        @GetMapping("/unprocessable")
        void unprocessable() {
            throw new CustomException(ErrorCode.PLAN_EXCEEDS_TRIP_PERIOD);
        }
    }
}
