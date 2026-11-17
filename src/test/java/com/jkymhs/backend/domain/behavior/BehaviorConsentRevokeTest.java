package com.jkymhs.backend.domain.behavior;

import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentSaveLogRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import com.jkymhs.backend.domain.behavior.repository.PreferenceProfileRepository;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.wish.BehaviorTestSupport;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BehaviorConsentRevokeTest extends BehaviorTestSupport {

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Autowired
    private PreferenceProfileRepository preferenceProfileRepository;

    @Autowired
    private ContentViewRepository contentViewRepository;

    @Autowired
    private ContentSaveLogRepository contentSaveLogRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("동의 변경 API로 행동 데이터를 끄면 그 요청 안에서 행동 기록·프로필이 지워지고, 이후 행동은 쌓이지 않는다")
    void revokeDeletesBehaviorData() throws Exception {
        saveContents(attraction(1L, "경복궁", "11", "110"), attraction(2L, "덕수궁", "11", "140"));
        User user = saveUser(true);
        String token = token(user);
        mockMvc.perform(auth(get("/api/contents/1"), token)).andExpect(status().isOk());
        mockMvc.perform(auth(post("/api/wishes/1"), token)).andExpect(status().isOk());
        assertThat(behaviorEventRepository.count()).isEqualTo(2);
        assertThat(preferenceProfileRepository.count()).isEqualTo(1);

        mockMvc.perform(auth(put("/api/members/me/consents"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consents": [{"type": "BEHAVIOR_DATA", "agreed": false}]}
                                """))
                .andExpect(status().isOk());

        assertThat(behaviorEventRepository.count()).isZero();
        assertThat(preferenceProfileRepository.count()).isZero();
        // 조회 수는 전체 통계(인기 점수)라서 동의와 상관없이 남는다
        assertThat(contentViewRepository.count()).isEqualTo(1);

        mockMvc.perform(auth(post("/api/wishes/2"), token)).andExpect(status().isOk());
        assertThat(behaviorEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("탈퇴하면 조회 기록까지 모든 행동 데이터가 지워진다")
    void withdrawDeletesEverything() throws Exception {
        saveContents(attraction(1L, "경복궁", "11", "110"));
        User user = saveUser(true);
        String token = token(user);
        mockMvc.perform(auth(get("/api/contents/1"), token)).andExpect(status().isOk());
        eventPublisher.publishEvent(new BehaviorSignalEvent(user.getId(), 1L, BehaviorSignalType.SCHEDULE_SAVE,
                "kids", null, LocalDateTime.of(2026, 11, 9, 10, 0)));
        assertThat(contentSaveLogRepository.count()).isEqualTo(1);

        mockMvc.perform(auth(delete("/api/members/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(contentViewRepository.count()).isZero();
        assertThat(contentSaveLogRepository.count()).isZero();
        assertThat(behaviorEventRepository.count()).isZero();
        assertThat(preferenceProfileRepository.count()).isZero();
    }
}
