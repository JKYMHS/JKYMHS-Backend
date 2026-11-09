package com.jkymhs.backend.domain.behavior;

import com.jkymhs.backend.domain.behavior.entity.BehaviorEvent;
import com.jkymhs.backend.domain.behavior.entity.ContentView;
import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import com.jkymhs.backend.domain.behavior.repository.PreferenceProfileRepository;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.wish.BehaviorTestSupport;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상세 조회 기록: 로그인한 회원만, 콘텐츠·날짜(KST)마다 한 번.
 */
class ContentViewTest extends BehaviorTestSupport {

    @Autowired
    private ContentViewRepository contentViewRepository;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Autowired
    private PreferenceProfileRepository preferenceProfileRepository;

    @BeforeEach
    void setUp() {
        saveContents(attraction(1L, "경복궁", "11", "110"));
    }

    @Test
    @DisplayName("같은 날 여러 번 열어도 조회 기록과 VIEW 행동은 하나이고, 다음 날 다시 열면 하나 더 생긴다")
    void oncePerDay() throws Exception {
        User user = saveUser(true);
        String token = token(user);

        mockMvc.perform(auth(get("/api/contents/1"), token)).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/contents/1"), token)).andExpect(status().isOk());

        assertThat(contentViewRepository.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.getUserId()).isEqualTo(user.getId());
                    assertThat(view.getContentId()).isEqualTo(1L);
                    assertThat(view.getViewDate()).isEqualTo(LocalDate.of(2026, 11, 9));
                });
        assertThat(behaviorEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(BehaviorSignalType.VIEW);
                    assertThat(event.getWeight()).isEqualTo(1);
                    assertThat(event.getLcls2()).isEqualTo("HS01");
                    assertThat(event.getCompanion()).isNull();
                });

        clock.advance(Duration.ofDays(1));
        mockMvc.perform(auth(get("/api/contents/1"), token)).andExpect(status().isOk());

        assertThat(contentViewRepository.findAll()).extracting(ContentView::getViewDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10));
        assertThat(behaviorEventRepository.findAll()).extracting(BehaviorEvent::getEventType)
                .containsOnly(BehaviorSignalType.VIEW).hasSize(2);
    }

    @Test
    @DisplayName("비로그인 조회는 기록하지 않는다")
    void anonymousNotCounted() throws Exception {
        mockMvc.perform(get("/api/contents/1")).andExpect(status().isOk());

        assertThat(contentViewRepository.count()).isZero();
        assertThat(behaviorEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("행동 데이터에 동의하지 않은 회원은 조회 수(인기 점수용)만 남고 행동 기록·프로필은 남지 않는다")
    void withoutConsent() throws Exception {
        mockMvc.perform(auth(get("/api/contents/1"), token(saveUser(false)))).andExpect(status().isOk());

        assertThat(contentViewRepository.count()).isEqualTo(1);
        assertThat(behaviorEventRepository.count()).isZero();
        assertThat(preferenceProfileRepository.count()).isZero();
    }
}
