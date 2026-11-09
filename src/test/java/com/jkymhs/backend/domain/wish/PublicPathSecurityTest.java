package com.jkymhs.backend.domain.wish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig의 공개 경로: 탐색 조회(GET)는 토큰 없이 열리고, 출발지 검색·찜·쓰기 요청은 로그인이 필요하다.
 */
class PublicPathSecurityTest extends BehaviorTestSupport {

    @BeforeEach
    void setUp() {
        saveContents(attraction(1L, "경복궁", "11", "110"));
    }

    @Test
    @DisplayName("지역·카테고리·탐색 목록·자동완성·상세·주변·비교 조회는 토큰 없이 200이다")
    void publicGets() throws Exception {
        mockMvc.perform(get("/api/regions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/travel-styles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/contents").param("tab", "place")).andExpect(status().isOk());
        mockMvc.perform(get("/api/contents/autocomplete").param("q", "경복")).andExpect(status().isOk());
        mockMvc.perform(get("/api/contents/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/contents/1/nearby-picks")).andExpect(status().isOk());
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT")
                .param("lat", "37.5665").param("lng", "126.978")).andExpect(status().isOk());
        mockMvc.perform(get("/api/contents/lookup").param("ids", "1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("출발지 검색·좌표→주소·찜은 토큰 없이 401이다")
    void protectedPaths() throws Exception {
        mockMvc.perform(get("/api/places/search").param("q", "용산역"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/places/reverse-geocode").param("lat", "37.5").param("lng", "127"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/wishes").param("tab", "place"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("탐색 경로라도 GET이 아니면 열지 않는다")
    void nonGetOnContentsRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/contents/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
