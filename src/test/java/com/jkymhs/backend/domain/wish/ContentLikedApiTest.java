package com.jkymhs.backend.domain.wish;

import com.jkymhs.backend.global.security.jwt.JwtProperties;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 탐색 API의 liked: 토큰이 있으면 그 회원의 찜 여부, 없거나 만료됐으면 false (401이 아니라 비로그인으로 본다).
 */
class ContentLikedApiTest extends BehaviorTestSupport {

    @Autowired
    private JwtProperties jwtProperties;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        saveContents(attraction(1L, "경복궁", "11", "110"), attraction(2L, "덕수궁", "11", "140"));
        token = token(saveUser(true));
        mockMvc.perform(auth(post("/api/wishes/1"), token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하면 목록·비교·상세에 찜한 콘텐츠만 liked=true다")
    void likedWithToken() throws Exception {
        mockMvc.perform(auth(get("/api/contents").param("tab", "place").param("sort", "name"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁")))
                .andExpect(jsonPath("$.items[*].liked").value(contains(true, false)));
        mockMvc.perform(auth(get("/api/contents/lookup").param("ids", "2,1"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].liked").value(contains(false, true)));
        mockMvc.perform(auth(get("/api/contents/1"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.wishCount").value(1));
    }

    @Test
    @DisplayName("토큰이 없으면 liked=false다")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].liked").value(contains(false, false)));
        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    @DisplayName("만료된 토큰으로 공개 API를 부르면 401이 아니라 비로그인으로 200이다")
    void expiredTokenOnPublicPath() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(
                jwtProperties.secret(), Duration.ofSeconds(-1), jwtProperties.refreshTokenExpiration()));
        String expired = expiredProvider.createAccessToken(userRepository.findAll().getFirst().getId());

        mockMvc.perform(get("/api/contents/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));
        mockMvc.perform(get("/api/contents").param("tab", "place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].liked").value(contains(false, false)));
    }
}
