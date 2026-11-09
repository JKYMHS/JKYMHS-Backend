package com.jkymhs.backend.domain.wish;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.wish.repository.WishRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WishApiTest extends BehaviorTestSupport {

    @Autowired
    private WishRepository wishRepository;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        saveContents(attraction(1L, "경복궁", "11", "110"),
                festival(2L, "가을 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)));
        user = saveUser(true);
        token = token(user);
    }

    @Test
    @DisplayName("찜을 두 번 추가해도 행은 하나이고 찜 수는 1만 오른다")
    void addIsIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(auth(post("/api/wishes/1"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentId").value(1))
                    .andExpect(jsonPath("$.liked").value(true))
                    .andExpect(jsonPath("$.wishCount").value(1));
        }

        assertThat(wishRepository.count()).isEqualTo(1);
        assertThat(wishCountOf(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("찜을 두 번 지워도 두 번 다 200이고 찜 수는 한 번만 내려간다")
    void removeIsIdempotent() throws Exception {
        mockMvc.perform(auth(post("/api/wishes/1"), token)).andExpect(status().isOk());
        mockMvc.perform(auth(post("/api/wishes/1"), token(saveUser(false)))).andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(auth(delete("/api/wishes/1"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false))
                    .andExpect(jsonPath("$.wishCount").value(1));
        }

        assertThat(wishRepository.count()).isEqualTo(1);
        assertThat(wishCountOf(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 콘텐츠나 없는 콘텐츠는 찜할 수 없다 (404)")
    void addDeletedOrUnknown() throws Exception {
        contentService.markDeleted(List.of(2L));

        mockMvc.perform(auth(post("/api/wishes/2"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
        mockMvc.perform(auth(post("/api/wishes/999"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
        assertThat(wishRepository.count()).isZero();
    }

    @Test
    @DisplayName("찜한 뒤 삭제된 콘텐츠도 찜을 지울 수 있고, 아예 없는 id는 404 '존재하지 않는 장소입니다.'다")
    void removeDeletedAndUnknown() throws Exception {
        mockMvc.perform(auth(post("/api/wishes/2"), token)).andExpect(status().isOk());
        contentService.markDeleted(List.of(2L));

        mockMvc.perform(auth(delete("/api/wishes/2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.wishCount").value(0));
        mockMvc.perform(auth(delete("/api/wishes/999"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 장소입니다."));
        assertThat(wishRepository.count()).isZero();
    }

    @Test
    @DisplayName("로그인하지 않으면 찜 추가·삭제·목록 모두 401이다")
    void unauthorized() throws Exception {
        mockMvc.perform(post("/api/wishes/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(delete("/api/wishes/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/wishes").param("tab", "place"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("탈퇴 요약에 찜 수가 나오고, 탈퇴하면 찜이 지워지고 콘텐츠 찜 수가 내려간다")
    void withdraw() throws Exception {
        mockMvc.perform(auth(post("/api/wishes/1"), token)).andExpect(status().isOk());
        mockMvc.perform(auth(post("/api/wishes/2"), token)).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/members/me/withdraw-summary"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishCount").value(2));

        mockMvc.perform(auth(delete("/api/members/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(wishRepository.count()).isZero();
        assertThat(wishCountOf(1L)).isZero();
        assertThat(wishCountOf(2L)).isZero();
    }
}
