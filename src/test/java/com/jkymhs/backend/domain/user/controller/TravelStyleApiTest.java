package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelStyleApiTest extends IntegrationTestSupport {

    @Test
    @DisplayName("로그인 없이 여행 스타일 7개를 화면 순서대로 준다 (id는 소문자 코드)")
    void list() throws Exception {
        mockMvc.perform(get("/api/travel-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styles.length()").value(7))
                .andExpect(jsonPath("$.styles[*].id").value(org.hamcrest.Matchers.contains(
                        "rest", "activity", "culture", "food", "nature", "shopping", "festival")))
                .andExpect(jsonPath("$.styles[0].name").value("휴식"))
                .andExpect(jsonPath("$.styles[0].description").value("카페·스파처럼 여유롭게"))
                .andExpect(jsonPath("$.styles[6].name").value("축제·공연"));
    }

    @Test
    @DisplayName("공개는 조회(GET)만이다. 같은 경로라도 다른 메서드는 로그인이 필요하다")
    void onlyGetIsPublic() throws Exception {
        mockMvc.perform(post("/api/travel-styles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
